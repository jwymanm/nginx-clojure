/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;

import nginx.clojure.ChannelListener;
import nginx.clojure.Coroutine;
import nginx.clojure.MessageListener;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxRequest;
import nginx.clojure.RawMessageListener;
import nginx.clojure.SuspendExecution;

public class RequestRawMessageAdapter implements RawMessageListener<NginxRequest> {

	public final static ConcurrentLinkedQueue<Coroutine> pooledCoroutines = new ConcurrentLinkedQueue<Coroutine>();
	
	private final static ConcurrentHashMap<NginxRequest, RequestOrderedRunnable> lastFutureTasks = new ConcurrentHashMap<NginxRequest, RequestOrderedRunnable>();
	
	public final static class RequestOrderedRunnable extends FutureTask<String> {
		protected Runnable action;
		protected RequestOrderedRunnable last;
		protected NginxRequest request;
		
		public RequestOrderedRunnable(Runnable action, NginxRequest request) {
			super(action, "");
			this.action = action;
			this.request = request;
			this.last = lastFutureTasks.put(request, this);
		}

		@Override
		public void run() {
			try {
				if (last != null) {
					last.get();
				}
				lastFutureTasks.remove(request, this);
				super.run();
			} catch (Throwable e) {
				super.cancel(false);
				NginxClojureRT.log.error(e);
			} 
		}
	}
	
	@Override
	public void onClose(final NginxRequest req) {
		if (req.isReleased()) {
			return;
		}
		
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onClose!", req.nativeRequest(), req.uri());
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (listeners != null && !listeners.isEmpty()) {
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							ChannelListener<Object> l = en.getValue();
							if (l instanceof MessageListener) {
								if (!req.channel().isClosed()) {
									((MessageListener) l).onClose(en.getKey(), 1006, null);
								}
							}else {
								l.onClose(en.getKey());
							}
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onClose Error!", req.nativeRequest()), e);
						}
					}
					req.tagReleased();
					
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}else {
			req.tagReleased();
		}
	}
	
	public void onClose(final NginxRequest req, long message) {
		if (req.isReleased()) {
			return;
		}
		
		int size = (int) (( message >> 48 ) & 0xffff) - 2;
		long address = message << 16 >> 16;
		final int status = size >= 0 ? ((0xffff & (NginxClojureRT.UNSAFE.getByte(NginxClojureRT.UNSAFE.getAddress(address)) << 8))
					| (0xff & NginxClojureRT.UNSAFE.getByte(NginxClojureRT.UNSAFE.getAddress(address)+1))) : 1000;
		
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onClose2, status=%d", req.nativeRequest(), req.uri(), status);
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (listeners != null && !listeners.isEmpty()) {
			ByteBuffer bb = NginxClojureRT.pickByteBuffer();
			CharBuffer cb = NginxClojureRT.pickCharBuffer();
			final String txt = size > 0 ? NginxClojureRT.fetchStringValidPart(address, 2, size, MiniConstants.DEFAULT_ENCODING, bb, cb) : null;
			
			if (size > 0) {
				int invalidNum = bb.remaining();
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.getLog().debug("onClose2 fetchStringValidPart : %d", invalidNum);
				}
				NginxClojureRT.UNSAFE.putAddress(address, NginxClojureRT.UNSAFE.getAddress(address) - invalidNum);
			}
			
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							ChannelListener<Object> l = en.getValue();
							if (l instanceof MessageListener) {
								((MessageListener) l).onClose(en.getKey(), status, txt);
							}
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onClose Error!", req.nativeRequest()), e);
						}
					}
					if (req.channel() != null) {
						try {
							req.channel().close();
						} catch (IOException e) {
							NginxClojureRT.log.error(String.format("#%d: onClose Error!", req.nativeRequest()), e);
						}
					}else {
						req.tagReleased();
					}
					
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}else {
			req.tagReleased();
		}
	}

	@Override
	public void onConnect(final long status, final NginxRequest req) {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onConnect, status=%d", req.nativeRequest(), req.uri(), status);
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (listeners != null && !listeners.isEmpty()) {
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							en.getValue().onConnect(status, req);
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onConnect Error!", req.nativeRequest()), e);
						}
					}				
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}
	}

	@Override
	public void onRead(final long status, final NginxRequest req) {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onRead, status=%d", req.nativeRequest(), req.uri(), status);
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (listeners != null && !listeners.isEmpty()) {
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							en.getValue().onRead(status, en.getKey());
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onRead Error!", req.nativeRequest()), e);
						}
					}		
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}
	}

	@Override
	public void onWrite(final long status, final NginxRequest req) {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onWrite, status=%d", req.nativeRequest(), req.uri(), status);
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (listeners != null && !listeners.isEmpty()) {
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							en.getValue().onWrite(status, en.getKey());
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onWrite Error!", req.nativeRequest()), e);
						}
					}	
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}
	}
	
	@Override
	public void onBinaryMessage(final NginxRequest req, long message, final boolean remining, boolean first) {
		int size = (int) (( message >> 48 ) & 0xffff);
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onBinaryMessage! size=%d, rem=%s, first=%s, pm=%d", req.nativeRequest(), req.uri(), size, remining, first, NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16));
		}
		if (size <= 0 && !first && remining) {
			return;
		}
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		
		if (listeners != null && !listeners.isEmpty()) {
			final ByteBuffer bb = ByteBuffer.allocate(size);
			NginxClojureRT.ngx_http_clojure_mem_copy_to_obj(NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16), bb.array(), MiniConstants.BYTE_ARRAY_OFFSET, size);
			bb.limit(size);
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
					for (int i = localListeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
						try {
							ChannelListener<Object> l = en.getValue();
							if (l instanceof MessageListener) {
								((MessageListener) l).onBinaryMessage(en.getKey(), bb, remining);
							}
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onBinaryMessage Error!", req.nativeRequest()), e);
						}
					}
				}
				@Override
				public void onFinished(Coroutine c) {
					pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
			}
		}
	
	}
	
	@Override
	public void onTextMessage(final NginxRequest req, long message, final boolean remining, boolean first) {
		int size = (int) (( message >> 48 ) & 0xffff);
		final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("#%d: request %s onTextMessage! size=%d, rem=%s, first=%s, pm=%d, lns=%d",
					req.nativeRequest(), req.uri(), size, remining, first, NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16), listeners.size());
		}
		if (listeners != null && !listeners.isEmpty()) {
			ByteBuffer bb = NginxClojureRT.pickByteBuffer();
			CharBuffer cb = NginxClojureRT.pickCharBuffer();
			long address = message << 16 >> 16;
			final String txt = NginxClojureRT.fetchStringValidPart(address, 0,  size, MiniConstants.DEFAULT_ENCODING, bb, cb);
			int invalidNum = bb.remaining();
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.getLog().debug("onTextMessage fetchStringValidPart : %d", invalidNum);
			}
			NginxClojureRT.UNSAFE.putAddress(address, NginxClojureRT.UNSAFE.getAddress(address) - invalidNum);
			if (txt.length() > 0 || first || !remining) {
				if ( (txt.length() == 0 || !remining) && invalidNum != 0) {
					return;
				}
				
				Runnable action = new Coroutine.FinishAwaredRunnable() {
					@Override
					public void run() throws SuspendExecution {
						List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = getSafeListeners(listeners);
						for (int i = localListeners.size() - 1; i > -1; i--) {
							java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = localListeners.get(i);
							try {
								ChannelListener<Object> l = en.getValue();
								if (l instanceof MessageListener) {
									((MessageListener) l).onTextMessage(en.getKey(), txt, remining);
								}
							}catch(Throwable e) {
								NginxClojureRT.log.error(String.format("#%d: onTextMessage Error!", req.nativeRequest()), e);
							}
						}
					}
					@Override
					public void onFinished(Coroutine c) {
						pooledCoroutines.add(c);
					}
				};
				
				if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
					Coroutine coroutine = pooledCoroutines.poll();
					if (coroutine == null) {
						coroutine = new Coroutine(action);
					}else {
						coroutine.reset(action);
					}
					coroutine.resume();
				}else if (NginxClojureRT.workers == null) {
					action.run();
				}else {
					NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable(action, req));
				}
			}
		}
	}

	private List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> getSafeListeners(
			final List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners) {
		List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> localListeners = listeners;
		//When at main thread listener method maybe trigger close event which will cause listeners to be cleared
		//so we need copy listeners for safe usage. When not at main thread all close operation will be post to 
		//FIFO queue and listeners won't be changed immediate so we need not copy them.
		if (Thread.currentThread() == NginxClojureRT.NGINX_MAIN_THREAD) {
			localListeners = new ArrayList<java.util.AbstractMap.SimpleEntry<Object,ChannelListener<Object>>>();
			localListeners.addAll(listeners);
		}
		return localListeners;
	}

}
