(ns build-plugin
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [shade.core :as shade]
            [lambdaisland.classpath :as licp])
  (:import java.nio.file.FileSystems
           java.nio.file.Path
           java.nio.file.Paths
           java.nio.file.Files
           java.nio.file.FileVisitOption
           java.nio.file.StandardOpenOption
           java.nio.charset.StandardCharsets
           java.net.URI))

(def version (str (slurp ".VERSION_PREFIX") "." (b/git-count-revs nil)))
(def class-dir "target/classes")

(def include-libs
  '[org.clojure/clojure
    org.clojure/spec.alpha
    org.clojure/core.specs.alpha
    org.clojure/java.classpath

    com.lambdaisland/witchcraft
    com.github.cryptomorin/XSeries
    lambdaisland/data-printers
    org.reflections/reflections
    org.javassist/javassist
    javax.servlet/javax.servlet-api
    ])

(comment
  (keys (:libs (b/create-basis {:project "deps.edn"
                                :aliases []})))
  )

(def shadings
  {})

(defn clean [params]
  (b/delete {:path "target"})
  params)

;; clj-yaml misbehaves with Glowstone on the classpath, because Glowstone
;; vendors an old version of SnakeYaml. For our needs this is good enough.
(defn yaml-str [m]
  (apply str
         (map (fn [[k v]]
                (str (name k) ": " (pr-str v) "\n")) m)))

(defn plugin-yml [{:keys [target-dir api-version]}]
  (spit (io/file target-dir "plugin.yml")
        (yaml-str
         {:main "com.lambdaisland.PrivateChestsPlugin"
          :name "PrivateChests"
          :version version
          :author "lambdaisland"
          :description "Simple private chests and shops"
          :api-version api-version
          :softdepend []})))

(defn shade-jar [in out]
  (when (seq shadings)
    ;; java
    (shade/shade in out shadings)))

(defn build [{:keys [env api-version] :as params
              :or {api-version "1.18"}}]
  (let [lib 'com.lambdaisland/private-chests
        basis (b/create-basis {:project "deps.edn"
                               :aliases [()]})
        jar-file (format "target/%s-%s-for-%s.jar"
                         (name lib)
                         version
                         api-version)]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]})
    #_(binding [*compiler-options* (assoc *compiler-options* :direct-linking true)
                *compile-path* "target/classes"]
        (run! compile '[lambdaisland.private-chests
                        lambdaisland.witchcraft
                        lambdaisland.witchcraft.events])
        )
    (b/copy-dir {:src-dirs ["src"]
                 :target-dir class-dir})
    (b/javac {:src-dirs ["java"]
              :class-dir class-dir
              :basis basis
              :javac-opts ["-source" "11" "-target" "11"]})
    (plugin-yml {:target-dir class-dir :api-version api-version})
    #_(b/jar {:class-dir class-dir
              :jar-file jar-file})
    (b/uber {:class-dir class-dir
             :uber-file jar-file
             :basis (update (b/create-basis {:project "deps.edn"
                                             :aliases []})
                            :libs
                            select-keys
                            include-libs)})
    #_(shade-jar jar-file (str/replace jar-file ".jar" "-shaded.jar")))
  params)

(comment (:libs
          (update (b/create-basis {:project "deps.edn"
                                   :aliases []})
                  :libs
                  select-keys
                  '[org.clojure/clojure
                    org.clojure/spec.alpha
                    com.github.cryptomorin/XSeries
                    lambdaisland/data-printers]))

)
