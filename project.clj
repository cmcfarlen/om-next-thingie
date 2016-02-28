(defproject om-next-thingie "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.5.3"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [sablono "0.6.2"]
                 [org.omcljs/om "1.0.0-alpha30"]
                 [com.datomic/datomic-free "0.9.5350"]
                 [com.taoensso/sente "1.8.0"]
                 [compojure "1.4.0"]
                 [com.stuartsierra/component "0.3.1"]]

  :plugins [[lein-cljsbuild "1.1.2" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.0-6"]
                                  [ring/ring-devel "1.4.0"]]
                   :source-paths ["env/dev"]}}

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  )
