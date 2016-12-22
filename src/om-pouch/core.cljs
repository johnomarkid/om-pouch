(ns om-pouch.core
 (:require-macros [cljs.core.async.macros :refer [go]])
 (:require [goog.dom :as gdom]
           [cljs.core.async :as async :refer [<! >! put! chan]]
           [om.next :as om :refer-macros [defui]]
           [om.dom :as dom]
           [clojure.string :as string]
           [caterpillar.pouch :as pouch]
           [cljsjs.pouchdb])
 (:import [goog Uri]
          [goog.net Jsonp]))

(enable-console-print!)

(def conn (pouch/create-db "test-caterpillar"))

(def init-todos [{:_id "1" :item "do the dishes"}
                 {:_id "2" :item "take out the trash"}])

(defmulti read om/dispatch)

(defmethod read :alldocs
  [{:keys [state ast]} key params]
  (let [st @state]

   {:value (get st key []) 
    :pouchpull ast}))
  
(defmulti mutate om/dispatch)

(defmethod mutate `putdoc
  [{:keys [state ast]} key params]

  {:action
   (fn []
     (println "action being called: "params))
   :remote true
   :pouchput ast})

(defui TodoList
  static om/IQueryParams
  (params [_]
          {:todo-input ""})
  static om/IQuery
  (query [this]
         [:alldocs])
  Object
  (render [this]
    (let [{:keys [alldocs]} (om/props this)
          {:keys [todo-input]} (om/get-params this)]
      (dom/div nil
        (dom/h2 nil "Pouch Todos")
        (dom/ul nil
                (map (fn [it]
                       (dom/li nil (:item it)))
                  alldocs))
        (dom/input #js {:key "todo-field"
                        :value todo-input
                        :onChange
                        (fn [e]
                          (om/set-query! this
                                         {:params {:todo-input (.. e -target -value)}}))})
        (dom/button 
                    #js {:onClick
                         (fn [e]
                           (let [new-item (:todo-input (om/get-params this))]
                            (om/transact! this `[(putdoc {:item ~new-item} :alldocs)])))}
                    "Add Todo")))))


(defn send-to-pouch [{:keys [pouchpull pouchput] :as remotes} cb]
  (when pouchput
    (println om/query->ast pouchput)
    (let [{[pouchput] :children} (om/query->ast pouchput)
          query (get-in pouchput [:params])]
     (println pouchput)))
      ;; now we actually have the doc structure we want {:item "todo"}
      ;; just need to merge with the id and other defaults
     ;; (go
       ;; (let [docs (<! (pouch/put-doc conn (merge query {:_id "11"})))]
         ;; (println "pouch res: " docs)
         ;; (let [new-doc (merge query {:_id (:id docs) :_rev (:rev docs)})]
           ;; (println "new doc: " new-doc))))))
           ;; does it merge?
          ;(cb {:alldocs new-doc}))))))
  (when pouchpull
    (println "trying to pull")
    (go
      (let [docs (<! (pouch/all-docs conn {:include_docs true}))]
        (as->
          (map #(:doc %) (:rows docs)) v
          (cb {:alldocs v}))))))

(def reconciler
  (om/reconciler
    {:state   {:results []}
     :parser  (om/parser {:read read :mutate mutate})
     :send    send-to-pouch
     :remotes [:pouchpull :pouchput]}))

(om/add-root! reconciler TodoList
  (gdom/getElement "app"))
