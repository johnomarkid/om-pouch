(ns om-pouch.pouch
 (:require [cljsjs.pouchdb]
           [cljs.core.async :refer [<! >! put! chan close!]])
 (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- hash-to-obj
  "Convert a CLJS structure to a JS object, yielding empty JS object for nil input"
  [obj]
  (let [jso (or (clj->js obj) (js-obj))]
    jso))

(defn- obj-to-hash
  "Convert a JS object to a hash, yielding nil for nil JS object"
  [obj]
  (if obj (js->clj obj :keywordize-keys true) {}))

(defn- create-error
  "Create an error hash from an error, having :error as the sole key"
  [err]
  (when err {:error (obj-to-hash err)}))

(defn create-result
  [jso]
  (obj-to-hash jso))

(defn- responder
  "Create am [err resp] callback putting a proper structure to the given channel"
  [c]
  (fn [err resp]
    (let [obj (or (create-error err) (create-result resp))]
      (put! c obj))))

(defn create-db
 "Create a new PouchDB database given optional name and options"
 [& [name & [options]]]
 (js/PouchDB. name (hash-to-obj options)))

(defn put-doc
  "Put a document, returning a channel to the result"
  [db doc & [options]]
  (let [opts (hash-to-obj options)
        js-doc (hash-to-obj doc)
        c (chan 1)]
   (.put db js-doc opts (responder c))
   c))

(defn create-index
  "Create an index so we can efficiently find."
  [db index]  
  (let [index (hash-to-obj index)
        c (chan 1)]
    (.createIndex db index (responder c))
    c))

(defn pouch-find
  "Find docs, returning channel to results"
  [db selector & [options]]
  (let [sel (hash-to-obj selector)
        c (chan 1)]
    (.find db sel (responder c))
    c))

(defn post-doc
  "Create a new document letting PouchDB generate the _id, returning a channel
   holding the result"
  [db doc & [options]]
  (let [opts (hash-to-obj options)
        js-doc (hash-to-obj doc)
        c (chan 1)]
   (.post db js-doc opts (responder c))
   c))

(defn bulk-docs
  "Create a batch of documents, returning channel to result"
  [db docs & [options]]
  (let [c (chan 1)]
   (.bulkDocs db (js-obj "docs" (hash-to-obj docs)) (hash-to-obj options) (responder c))
   c))

(defn get-doc
  "Get document given ID, returning a channel to the result"
  [db docid & [options]]
  (let [c (chan 1)]
    (.get db docid (hash-to-obj options) (responder c))
    c))

(defn all-docs
  "Fetch (all) documents, returning channel to result"
  [db & [options]]
  (let [c (chan 1)]
    (.allDocs db (hash-to-obj options) (responder c))
    c))

(defn destroy-db
  "Destroys a PouchDB database, returning a channel to the result"
  [db]
  (let [c (chan 1)]
    (.destroy db (responder c))
    c))

(defn remove-doc
  "Remove document, returning channel to result"
  [db doc & [options]]
  (let [c (chan 1)]
    (.remove db (hash-to-obj doc) (hash-to-obj options) (responder c))
    c))
