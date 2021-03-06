package nginx.clojure.java;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.clj.LazyRequestMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RequestMapTest {

	
	
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testJavaRequest() throws Throwable  {
		NginxJavaRequest kk = new NginxJavaRequest(null, 0);
		assertNotNull(kk);
		Field f = NginxJavaRequest.class.getDeclaredField("default_request_array");
		long off = HackUtils.UNSAFE.staticFieldOffset(f);
		Object base = HackUtils.UNSAFE.staticFieldBase(f);
		Object[] array = (Object[]) HackUtils.UNSAFE.getObject(base, off);
		for (int i = 0; i < array.length; i += 2) {
			array[i+1] = array[i].toString() + "--v";
		}
		NginxJavaRequest r = new NginxJavaRequest(null, 0, array);
		assertEquals(array.length/2, r.size());
		for (int i = 0; i < array.length; i += 2) {
			assertEquals(array[i]+"--v", r.get(array[i]));
		}
		assertEquals(MiniConstants.SCHEME+"--v", r.remove(MiniConstants.SCHEME));
		assertEquals(array.length/2-1, r.size());
		r.put(MiniConstants.SCHEME, MiniConstants.SCHEME+"--good");
		assertEquals(array.length/2, r.size());
		assertEquals(MiniConstants.SCHEME+"--good", r.get(MiniConstants.SCHEME));
		
	}
	
	public void testCljRequest() throws Throwable  {
		LazyRequestMap kk = new LazyRequestMap(null, 0);
		assertNotNull(kk);
		Field f = LazyRequestMap.class.getDeclaredField("default_request_array");
		long off = HackUtils.UNSAFE.staticFieldOffset(f);
		Object base = HackUtils.UNSAFE.staticFieldBase(f);
		Object[] array = (Object[]) HackUtils.UNSAFE.getObject(base, off);
		for (int i = 0; i < array.length; i += 2) {
			array[i+1] = array[i].toString() + "--v";
		}
		LazyRequestMap r = new LazyRequestMap(null, 0, new byte[]{0}, array);
		assertEquals(array.length/2, r.count());
		for (int i = 0; i < array.length; i += 2) {
			assertEquals(array[i]+"--v", r.valAt(array[i]));
		}
		assertEquals(nginx.clojure.clj.Constants.SCHEME+"--v", r.valAt(nginx.clojure.clj.Constants.SCHEME));
		r = (LazyRequestMap) r.without(nginx.clojure.clj.Constants.SCHEME);
		assertEquals(array.length/2-1, r.count());
		r = (LazyRequestMap) r.assoc(nginx.clojure.clj.Constants.SCHEME, nginx.clojure.clj.Constants.SCHEME+"--good");
		assertEquals(array.length/2, r.count());
		assertEquals(nginx.clojure.clj.Constants.SCHEME+"--good", r.valAt(nginx.clojure.clj.Constants.SCHEME));
		r = (LazyRequestMap) r.assoc("what", "good!");
		assertEquals(array.length/2+1, r.count());
		assertEquals("good!", r.valAt("what"));
	}

}
