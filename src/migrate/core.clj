(ns migrate.core
  (:require [datomic.api :as d]))

(defn has-attribute?
  [db attr]
  (d/entity db attr))

(def check-migration-fn
  (d/function {:lang "clojure"
               :params '[db mig-key txns]
               :code '(when-not (d/q '[:find ?tx .
                                       :in $ ?k
                                       :where [?tx :schema/migration ?k ?tx]] db mig-key)
                        (cons {:db/id #db/id [:db.part/tx -1]
                               :schema/migration mig-key}
                              txns))}))

(defn has-migration?
  [db migration-key]
  (boolean (d/q '[:find ?tx .
                  :in $ ?k
                  :where [?tx :schema/migration ?k ?tx]] db migration-key)))

(defn migrations
  [db]
  (d/q '[:find ?m
         :where [?tx :schema/migration ?m ?tx]] db))

(defn add-migration-key
  [conn]
  (d/transact conn [{:db/id (d/tempid :db.part/db)
                     :db/ident :schema/migration
                     :db/doc "tx attribute: name of the migration that created this transaction"
                     :db/valueType :db.type/keyword
                     :db/cardinality :db.cardinality/one
                     :db/index true
                     :db.install/_attribute :db.part/db}]))

(defn add-migration-fn
  [conn]
  (d/transact conn [{:db/id (d/tempid :db.part/user)
                     :db/ident :schema/migrate
                     :db/fn check-migration-fn}]))

(defn add-migration-schema
  [conn]
  (add-migration-key conn)
  (add-migration-fn conn))

(defn transact-migration
  [conn migration-key schema]
  (println "adding migration: " migration-key)
  (deref (d/transact conn [[:schema/migrate migration-key schema]])))

(defn ensure-schema
  [conn migration-key schema]
  (when-not (has-migration? (d/db conn) migration-key)
    (transact-migration conn migration-key schema)))

(defn migrate*
  [txns conn migration-map migrations]
  (reduce (fn [txns migration]
            (let [{:keys [requires txns]} (get migration-map migration)]
              (cond (has-migration? (d/db conn) migration)
                    txns

                    :else
                    (-> txns
                        (migrate* conn migration-map requires)
                        (conj
                         (ensure-schema conn migration txns))))))
          txns
          migrations))

(defn migrate
  [conn migration-map migrations]
  (migrate* [] conn migration-map migrations))


(defn scan-migrations
  [path]
  (let [f (clojure.java.io/as-file path)]
    (->> (file-seq f)
         (filter #(.endsWith (.getName %) ".edn"))
         (map (comp (partial clojure.edn/read-string {:readers *data-readers*}) slurp))
         (reduce (fn [a m]
                   (assoc a (:migration m) m)) {}))))

(defn scan-and-migrate
  [conn path]
  (let [m (scan-migrations path)]
    (migrate conn m (keys m))))

(comment

 (scan-migrations "migrations")

 (def local-uri "datomic:free://localhost:4334/migrate-test")
 (d/get-database-names "datomic:free://localhost:4334/*")

 (d/delete-database local-uri)
 (d/create-database local-uri)

 (has-attribute? (d/db conn) :geo/latitude)

 (def conn (d/connect local-uri))
 (d/release conn)

 (add-migration-schema conn)

 (scan-and-migrate conn "migrations")

 (add-migration-key conn)

 (migrations (d/db (user/datomic-conn)))

 (has-migration? (d/db conn) :test/schema1)

 (d/touch (d/entity (d/db conn) :test/name))

 (add-migration-fn conn)

 (next (:tx-data (deref (transact-migration conn :test/schema1 [{:db/id (d/tempid :db.part/db)
                                                                 :db/ident :test/name
                                                                 :db/valueType :db.type/string
                                                                 :db/cardinality :db.cardinality/one
                                                                 :db.install/_attribute :db.part/db}]))))

 (d/transact conn [])

 (-> (d/entity (d/db conn) :schema/migration-bobo)
     boolean
     )

 {:migration :inventory/add-chassis-info
  :requires [:inventory/base]
  :txns [ ] }
 )



