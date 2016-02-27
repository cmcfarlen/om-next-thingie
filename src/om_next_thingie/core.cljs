(ns om-next-thingie.core
  (:require
   [goog.dom :as gdom]
   [om.next :as om :refer-macros [defui]]
   [cljs.pprint :as pp]
   [sablono.core :as html :refer-macros  [html]]))

(enable-console-print!)

(defn create-item
  [state {:keys [item/id item/name]}]
  (let [id (if (om/tempid? id)
             (inc (reduce max 0 (keys (:item/by-id state))))
             id)]
    (-> state
        (assoc-in [:item/by-id id] {:item/id id :item/name name})
        (update-in [:item/all] conj [:item/by-id id]))))

(defn add-item
  [item-set item-id]
  (println "Adding item " item-id " to " item-set)
  (update-in item-set [:item.set/items] conj [:item/by-id item-id]))

(defmulti parser-read om/dispatch)

(defmethod parser-read :default
  [{:keys [state] :as e} k p]
  (println "default read: " k)
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value :not-found})))

(defmethod parser-read :item/sets
  [{:keys [query state] :as env} k params]
  (println "reading itemset: query = " query)
  (let [st @state]
    {:value (om/db->tree query (get st k) st)}))

(defmethod parser-read :item/all
  [{:keys [query state] :as env} k params]
  (println "reading itemall: query = " query)
  (let [st @state]
    {:value (om/db->tree query (get st k) st)}))

(defmethod parser-read :item/new
  [{:keys [state]} k _]
  {:value (second (find @state k))})

(defmulti parser-mutate om/dispatch)

(defmethod parser-mutate :default
  [_ k _]
  (println "no mutate for key:" k)
  {:value :not-found})

(defmethod parser-mutate 'item/update
  [{:keys [state ref] :as e} k new-props]
  (println "update: " (:ref e) " props: "  new-props)
  {:action
   (fn []
     (swap! state update-in ref merge new-props))})

(defmethod parser-mutate 'item/create
  [{:keys [state] :as e} k new-props]
  (println "Creating item: " new-props)
  {:action
   (fn []
     (swap! state create-item new-props))})

(defmethod parser-mutate 'item.set/add-item
  [{:keys [state ref] :as e} k {:keys [:item/id] :as new-props}]
  (println "add-item " new-props " ref " ref)
  {:action
   (fn []
     (swap! state update-in ref add-item id))})

(defmethod parser-mutate 'item/update-new
  [{:keys [state]} k {:keys [value]}]
  {:action
   (fn []
     (swap! state assoc :item/new value))})

(defmethod parser-mutate 'item/clear-new
  [{:keys [state]} k _]
  {:action
   (fn []
     (swap! state dissoc :item/new))})

(def item-data
  {:item/all [{:item/id 1 :item/name "Chris"}
              {:item/id 2 :item/name "Tammy"}
              {:item/id 3 :item/name "Copper"}]
   :item/sets [{:item.set/name "Set 1"
                :item.set/items [{:item/id 1 :item/name "Chris"} {:item/id 2 :item/name "Tammy"}]}
               {:item.set/name "Set 2"
                :item.set/items [{:item/id 3 :item/name "Copper"} {:item/id 1 :item/name "Chris"}]}]})

(defonce reconciler
  (om/reconciler
   {:state item-data
    :parser (om/parser {:read parser-read :mutate parser-mutate})}))

(defui Item
  static om/Ident
  (ident [this {:keys [item/id]}]
    [:item/by-id id])
  static om/IQuery
  (query [_]
    [:item/id :item/name])
  Object
  (render [this]
    (println "rendering item: " (-> this om/props :item/id))
    (let [props (om/props this)]
      (html [:li (str (:item/name props) "(" (:item/id props) ")")]))))

(def item (om/factory Item {:keyfn :item/id}))

(defui ItemSet
  static om/Ident
  (ident [this {:keys [item.set/name]}]
    [:item.set/by-name name])
  static om/IQuery
  (query [_]
    [:item.set/name {:item.set/items (om/get-query Item)}])
  Object
  (render [this]
    (println "rendering item set: " (-> this om/props keys))
    (let [{:keys [item.set/name item.set/items item/all]} (om/props this)
          set-ids (into #{} (map :item/id items))
          all-ids (into #{} (map :item/id all))
          missing-ids (clojure.set/difference all-ids set-ids)
          missing (filter #(missing-ids (:item/id %)) all)
          ]
      (html [:li
             [:h3 name]
             [:ul
              (map item items)
              [:li [:select {:ref "select"}
                    (map (fn [o] [:option {:value (:item/id o)
                                           :key (:item/id o)
                                           } (:item/name o)]) missing)]
               [:button {:on-click (fn [_]
                                     (let [new-id (cljs.reader/read-string (.. (om/react-ref this "select") -value))]
                                       (om/transact! this `[(item.set/add-item {:item/id ~new-id})])))} "Add Item"]]]]))))

(def item-set (om/factory ItemSet {:keyfn :item.set/name}))

(defui ItemView
  static om/IQuery
  (query [_]
    [{:item/all (om/get-query Item)}
     {:item/sets (om/get-query ItemSet)}
     :item/new])
  Object
  (render [this]
    (let [{:keys [item/sets item/all item/new]} (om/props this)]
      (html [:div
             [:h1 "Item sets"]
             [:ul
              (map #(item-set (assoc % :item/all all)) sets)]
             [:h1 "All Items"]
             [:ul
              (map item all)]
             [:h1 "New Item"]
             [:input {:type "text"
                      :value new
                      :placeholder "Enter item text"
                      :on-change (fn [e]
                                   (let [n (.. e -target -value)]
                                     (om/transact! this `[(item/update-new {:value ~n}) :item/new]))) }]
             [:button {:on-click (fn [_]
                                   (let [id (om/tempid)]
                                     (when new
                                       (om/transact! this `[(item/create {:item/id ~id :item/name ~new})
                                                            (item/clear-new)
                                                            :item/all])))
                                   )} "Add"]]))))


(om/add-root! reconciler ItemView (gdom/getElement "app"))

(comment
 (def norm-data (atom (om/tree->db ItemView item-data true)))
 (def p (om/parser {:read parser-read :mutate parser-mutate}))

 (p {:state norm-data} [{:item/sets (om/get-query ItemSet)}])

 (deref reconciler)

 (om/transact! reconciler '[(item/create {:item/id 4 :item/name "Rhianna"}) ])

 (om/transact! (om/ref->any reconciler [:item/by-id 3]) '[(item/update {:item/id 3 :item/name "Copper"})])
 (om/transact! (om/ref->any reconciler [:item.set/by-name "Set 1"]) '[(item.set/add-item {:item/id 4}) :item/sets])

 )

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
