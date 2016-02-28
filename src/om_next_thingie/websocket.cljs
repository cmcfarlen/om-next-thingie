(ns om-next-thingie.websocket
  (:require [cljs.core.async :refer [<! >! put! chan]]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [taoensso.sente :as sente])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce sente-state (sente/make-channel-socket! "/chsk" {:type :auto}))

(let [{:keys [chsk ch-recv send-fn state]} sente-state]
  (def chsk chsk)
  (def ch-cksh ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

; buffers up outgoing requests while websocket is coming up
(defonce send-chan (chan 32))
(defn send
  [data cb]
  (println "sending " data)
  (put! send-chan [data 5000 cb]))

(defmulti sse-dispatch first)

(defonce sente-loop
  (go-loop []
           (when-let [event (<! ch-cksh)]
             (let [{:keys [id ?data]} event]
               (case id
                 :chsk/state
                 (println "chsk/state" ?data)

                 :chsk/handshake
                 (go
                   (println "connected!")
                   (go-loop []
                            (let [m (<! send-chan)]
                              (apply chsk-send! m))
                            (recur)))

                 :chsk/recv
                 (sse-dispatch ?data)

                 (println "unhandled chsk event: " id ?data)))
             (recur))))

(defn sente-status
  [data owner]
  (reify
    om/IRender
    (render [_]
      (html [:span.pull-right {:style {:marginRight "10px"}}
              (if (:open? data)
                     [:span.label.label-success [:span.glyphicon.glyphicon-flash.spinning] (str (:uid data))]
                     [:span.label.label-danger [:span.glyphicon.glyphicon-exclamation-sign] "Websocket not connected"])]))))
(om/root
 sente-status
 chsk-state
 {:target (. js/document (getElementById "websocket"))})

