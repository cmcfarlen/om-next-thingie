(ns server.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [datomic.api :as d]
            [clojure.edn :as edn]))


(defroutes app-routes
  (GET "/" [] "Hi Haterz!"))


(def app (-> app-routes
             (wrap-resource "public")
             wrap-content-type
             wrap-not-modified))

