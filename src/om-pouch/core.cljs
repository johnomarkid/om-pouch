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
    (println "state on read: " st)
    (println "ast: " ast)
    (println "key: " key)
    (println "params: " params)
   {:value (get st key []) 
    :pouchpull ast}))
  
(defmulti mutate om/dispatch)

(defmethod mutate 'putdoc
  [{:keys [state ast]} key params]
  (println "state" state)
  (println "ast: " ast)
  (println "key: " key)
  (println "params" params)
  {:action
   (fn []
     (println "action being called"))
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
      (println "yoyoyoy: " alldocs)
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
                            (om/transact! this '[(putdoc {:item ~todo-input})]))}
                    "Add Todo")))))


;; TODO add item to pouch, test a query with params to generalize alldocs, test collisions
(defn send-to-chan [c]
  (fn [{:keys [search ttest]} cb]
    (when ttest
      (println "here's the ttest" ttest)
      (put! c "here we put stuff for pouch query"))
    (when search
      (let [{[search] :children} (om/query->ast search)
            query (get-in search [:params :query])]
        (put! c [query cb])))))

(defn send-to-pouch [{:keys [pouchpull pouchput] :as remotes} cb]
  (println "trying pouch send: " pouchput)
  ;; (when pouchput
    ;; (go
      ;; (let [docs (<! (pouch/put-doc conn ))])))
  (when pouchpull
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
