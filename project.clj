(defproject om-pouch "0.1.0-SNAPSHOT"
  :description "Om Next with remote PouchDB"
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.omcljs/om "1.0.0-alpha47"]
                 [cljsjs/pouchdb "6.0.4-0"]]

  :profiles
  {:dev
   {:dependencies [[figwheel-sidecar "0.5.8"]
                   [com.cemerick/piggieback "0.2.1"]
                   [org.clojure/tools.nrepl "0.2.10"]]
    :repl-options {:nrepl-middleware  [cemerick.piggieback/wrap-cljs-repl]}}})
