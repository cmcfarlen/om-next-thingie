(ns components.websocket
  (:require [taoensso.sente :as sente]
            [compojure.core :as compojure]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [com.stuartsierra.component :as component]))

(defrecord WebsocketComponent [server-adapter options websocket-dispatch ws-routes ws-router sente-info]
  component/Lifecycle
  (start [component]
    (println "Starting Sente")
    (let [sente-info (sente/make-channel-socket! server-adapter (or options {}))
          ws-dispatch (:dispatch websocket-dispatch)
          post-fn (:ajax-post-fn sente-info)
          ws-fn (:ajax-get-or-ws-handshake-fn sente-info)]
      (assoc component
             :sente-info sente-info
             :ws-routes (compojure/routes
                         (compojure/GET "/chsk" req (ws-fn req))
                         (compojure/POST "/chsk" req (post-fn req)))
             :ws-router (sente/start-chsk-router! (:ch-recv sente-info) ws-dispatch))))
  (stop [component]
    (println "Stopping Sente")
    (if-let [stop-fn (:ws-router component)]
      (assoc component :ws-router (stop-fn))
      component)))

(defrecord CombineWebsocketRoutes [input-handler websocket handler]
  component/Lifecycle
  (start [component]
    (println "Combining websocket routes")
    (assoc component :handler (-> (compojure/routes input-handler (:ws-routes websocket))
                                  wrap-keyword-params
                                  wrap-params)))
  (stop [component]
    (println "stopping combined routes")
    (dissoc component :handler)))

(defn websocket-component
  [server-adapter options]
  (map->WebsocketComponent {:server-adapter server-adapter
                            :options options}))


