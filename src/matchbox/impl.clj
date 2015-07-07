(ns matchbox.impl
  (require [matchbox.coerce :refer [ensure-kw-map]])
  (:import [com.firebase.client Firebase FirebaseError Firebase$AuthResultHandler
                                DataSnapshot Transaction Transaction$Handler AuthData
                                MutableData ValueEventListener ChildEventListener Transaction$Result
                                Firebase$CompletionListener]))

(defn throw-err [err]
  (throw (ex-info "Firebase error" {:error err})))

(defn completion-listener [handler err-handler]
  (assert err-handler)
   (reify Firebase$CompletionListener
     (^void onComplete [_ ^FirebaseError err ^Firebase ref]
       (if err
         (err-handler err)
         (handler ref)))))

(defn value-listener [handler err-handler]
  (assert err-handler)
  (reify ValueEventListener
    (^void onDataChange [_ ^DataSnapshot ds]   (handler ds))
    (^void onCancelled  [_ ^FirebaseError err] (err-handler err))))

(defn child-listener [{:keys [added changed moved removed cancelled]}]
  (reify ChildEventListener
    (^void onChildAdded   [_ ^DataSnapshot ds ^String old-key]
      (when added (added ds)))
    (^void onChildChanged [_ ^DataSnapshot ds ^String old-key]
      (when added (added ds)))
    (^void onChildMoved   [_ ^DataSnapshot ds ^String old-key]
      (when moved (moved ds)))
    (^void onChildRemoved [_ ^DataSnapshot ds]
      (when removed (removed ds)))
    (^void onCancelled [_ ^FirebaseError err]
      ;; double check this... perhaps better to do nothing
      ;; (does this happen on any unsubscribe?)
      ((or cancelled throw-err) err))))

(defn tx-handler [xform handler err-handler]
  (assert err-handler)
  (reify Transaction$Handler
    (^Transaction$Result doTransaction
      [_ ^MutableData d]
      (do (.setValue d (xform (.getValue d)))
          (Transaction/success d)))
    (^void onComplete
      [_ ^FirebaseError err ^boolean committed ^DataSnapshot ds]
      (if err
        (err-handler err committed)
        (when handler (handler ds))))))

(defn parse-auth [^AuthData result]
  {:uid           (.getUid result)
   :provider      (keyword (.getProvider result))
   :token         (.getToken result)
   :expires       (.getExpires result)
   :auth          (ensure-kw-map (.getAuth result))
   :provider-data (ensure-kw-map (.getProviderData result))})

(defn auth-handler [handler err-handler]
  (assert err-handler)
  (reify Firebase$AuthResultHandler
    (^void onAuthenticated [_ ^AuthData result]
      (handler result))
    (^void onAuthenticationError [_ ^FirebaseError err]
      (err-handler))))

;; TODO:
;; 1. Ensure downstream callers all handle hydration etc now [ie. tests]
;; 2. Ensure err-handler always passed, then remove assert [ie. tests]
;; 3. doc-strings?