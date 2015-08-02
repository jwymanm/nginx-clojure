(defproject nginx-clojure/nginx-clojure "0.4.1"
  :description "Nginx module for clojure or groovy or java programming"
  :url "https://github.com/nginx-clojure/nginx-clojure"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 ]
  :plugins [[lein-junit "1.1.7"]
            ;[venantius/ultra "0.1.9"]
            [lein-sub "0.3.0"]
            ]
  :sub ["nginx-tomcat8"]
  ;; CLJ source code path
  :source-paths ["src/clojure"]
  :target-path "target/"
  :global-vars {*warn-on-reflection* true
                *assert* false}
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-g" "-nowarn"]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classes"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :uberjar-exclusions [#"^test" #"\.java$"]
  :manifest {"Premain-Class" "nginx.clojure.wave.JavaAgent"
             "Can-Redefine-Classes" "true"
             "Can-Retransform-Classes" "true"
             }
  :profiles {
             :provided {
                        :dependencies [
                                  [org.clojure/clojure "1.7.0"]]
                        }
             :dev  {:dependencies [;only for test / compile usage
                                  [org.clojure/clojure "1.7.0"]
                                  [ring/ring-core "1.2.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.11"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  [mysql/mysql-connector-java "5.1.30"]
                                  ;for test file upload with ring-core which need it
                                  [javax.servlet/servlet-api "2.5"]
                                  [org.clojure/data.json "0.2.5"]
                                  [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                  [org.codehaus.groovy/groovy "2.3.4"]
                                  [stylefruits/gniazdo "0.4.0"]
                                  ]}
             :unittest {
                    :jvm-opts ["-javaagent:target/nginx-clojure-0.4.1.jar=mb"
                               "-Dnginx.clojure.wave.udfs=pure-clj.txt,compojure.txt,compojure-http-clj.txt"
                               "-Xbootclasspath/a:target/nginx-clojure-0.4.1.jar"]
                    :junit-options {:fork "on"}
                    :java-source-paths ["test/java" "test/clojure"]
                    :test-paths ["src/test/clojure"]
                    :source-paths ["test/clojure" "test/java" "test/nginx-working-dir/coroutine-udfs"]
                    :junit ["test/java"]
                    :compile-path "target/testclasses"
                    :dependencies [
                                  [org.clojure/clojure "1.7.0"]
                                  [ring/ring-core "1.2.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.11"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                  ;[mysql/mysql-connector-java "5.1.30"]
                                  ]
                        }
             :cljremotetest {
                                :java-source-paths ["test/java" "test/clojure"]
                                :test-paths ["src/test/clojure"]
                                :source-paths ["test/clojure" "test/java" "test/nginx-working-dir/coroutine-udfs"]
                                :compile-path "target/testclasses"
                                :test-selectors {:default (fn [m] (and (:remote m) (not (:async m)) (not (:jdbc m))))
                                                 :async :async
                                                 :jdbc :jdbc
                                                 :no-async (fn [m] (and (:remote m) (not (:async m))))
                                                 :access-handler :access-handler
                                                 :rewrite-handler :rewrite-handler
                                                 :websocket :websocket
                                                 :keepalive :keepalive
                                                 :all :remote}
                                :dependencies [
                                              [org.clojure/clojure "1.7.0"]
                                              [ring/ring-core "1.2.1"]
                                              [compojure "1.1.6"]
                                              [clj-http "0.7.8"]
                                              [junit/junit "4.11"]
                                              [org.clojure/java.jdbc "0.3.3"]
                                              [org.clojure/tools.nrepl "0.2.3"]
                                              ;for test file upload with ring-core which need it
                                              [javax.servlet/servlet-api "2.5"]
                                              [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                              [org.clojure/data.json "0.2.5"]
                                              [stylefruits/gniazdo "0.4.0"]
                                              ;[mysql/mysql-connector-java "5.1.30"]
                                              ]
                                    }             
             })
