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


