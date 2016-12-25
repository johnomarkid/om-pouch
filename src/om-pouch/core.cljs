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
  [{:keys [state ast query]} key params]
  (let [st @state]
    (println "sssst: " st)
    (if (contains? st key)
      {:value (into [] (map #(get-in st %)) (get st key))}
      {:alldocs ast})))

(defmethod read :default
  [{:keys [state ast]} key params]
  (let [st @state]
    {:value (get st key nil)}))
  
(defmulti mutate om/dispatch)

(defmethod mutate 'item/replace
  [{:keys [state]} key params]
  (println "TRYING NEW: " state)
  (println "params: " params)
  {:remote true
   :action
   (fn []
     (println "action in mutate"))})
  

(defmethod mutate `pouchput
  [{:keys [state ast]} key params]
    ;; optimistic update
 {:remote true
  :action
  (fn []
    (let [id (:_id params)]
     (swap! state (fn [v]
                    (as->
                      (update v :alldocs conj [:item/by-id id]) v
                      (update v :item/by-id merge {id params}))))))
  :pouchput ast})

(defui Row
  static om/Ident
  (ident [this {:keys [_id]}]
         [:item/by-id _id])
  static om/IQuery
  (query [this]
         '[:_id :item])  
  Object
  (render [this]
          (let [{:keys [item]} (om/props this)]
            (dom/li nil item))))

(def row-view (om/factory Row {:keyfn :_id}))

(defui ListView
  Object
  (render [this]
          (let [list (om/props this)]
            (apply dom/ul nil
                   (map row-view list)))))  

(def list-view (om/factory ListView))

(defui NewTodo
  static om/IQuery
  (query [this]
         '[:pouchput])
  static om/IQueryParams
  (params [_]
          {:todo-input ""})
  Object
  (render [this]
      (let [{:keys [todo-input]} (om/get-params this)
            {:keys [parent]} (om/get-computed this)]
        (dom/div nil
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
                             (om/transact! parent `[(pouchput {:item ~new-item :_id ~(om/tempid)})])))}
                     "Add Todo")))))

(def new-todo (om/factory NewTodo))

(defui TodoList
  static om/IQuery
  (query [this]
    (let [subquery (om/get-query Row)]
      `[{:alldocs ~subquery}]))
  Object
  (render [this]
    (let [{:keys [alldocs]} (om/props this)]
      (dom/div nil
        (dom/h2 nil "Pouch Todos")
        (list-view alldocs)
        (new-todo (om/computed {} {:parent this}))))))


(defn send-to-pouch [{:keys [alldocs pouchput]} cb]
  (when pouchput
    (let [{[pouchput] :children} (om/query->ast pouchput)
          query (get-in pouchput [:params])]
      
      (let [id (:_id query)
            nitem ;[[:item/by-id id] 
                   {:_id id :item "I JUST CHANGED YOU"}]
        (cb nitem))))
      ;; just need to merge with the id and other defaults
     ;; (go
       ;; (let [docs (<! (pouch/put-doc conn (merge query {:_id "11"})))]
         ;; (println "pouch res: " docs)
         ;; (let [new-doc (merge query {:_id (:id docs) :_rev (:rev docs)})]
           ;; (println "new doc: " new-doc))))))
           ;; does it merge?
          ;(cb {:alldocs new-doc}))))))
  (when alldocs
    (println "are we trying?")
    (go
      (let [docs (<! (pouch/all-docs conn {:include_docs true}))]
        (as->
          (map #(:doc %) (:rows docs)) v
          (vec v)
          (cb {:alldocs v}))))))


(defn def-merge [reconciler state res query]
  {:keys    (into [] (remove symbol?) (keys res))
   :next    (om/merge-novelty! reconciler state res query)})

(def init-data (atom {}))

(def reconciler
  (om/reconciler
    {:state   init-data
     :parser  (om/parser {:read read :mutate mutate})
     :normalize true
     :send    send-to-pouch
     :remotes [:alldocs :pouchput]
     :merge def-merge}))


(om/add-root! reconciler TodoList
  (gdom/getElement "app"))
