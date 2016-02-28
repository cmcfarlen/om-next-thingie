(ns components.datomic
  (:require [datomic.api :as d]
            [clojure.core.async :as async :refer [<! >! go <!!]]
            [migrate.core :as migrate]
            [com.stuartsierra.component :as component]))

(defrecord Datomic [uri conn]
  component/Lifecycle
  (start [component]
    (let [created? (d/create-database uri)
          c (d/connect uri)]
      (when created?
        (println "database" uri "created")
        (migrate/add-migration-schema c))
      (println "scanning new migrations...")
      (migrate/scan-and-migrate c "migrations")
      (println "done!")
      (assoc component
             :conn c)))
  (stop [component]
    (assoc component
           :conn (d/release conn))))

(defn datomic-component
  [uri]
  (map->Datomic {:uri uri}))

