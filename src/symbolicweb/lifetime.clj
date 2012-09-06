(in-ns 'symbolicweb.core)

;;; Lifetime tracking
;;;;;;;;;;;;;;;;;;;;;
;;
;; Used to:
;;
;;   * Keep track of the lifetime (duration) of connections between Observables and Observers.
;;
;;   * Keep track of visibility in a UI context. E.g. when the user navigates away from a "page", the widgets all
;;     monitoring some Model switch from visible to non visible. Cleanup etc. might be needed on both client and server end.
;;
;;   * ..do many other things.



(defprotocol ILifetime
  (add-lifetime-child [lifetime child])
  (remove-lifetime-child [lifetime child])

  (add-lifetime-activation-fn [lifetime fn])
  (handle-lifetime-activation [lifetime])

  (add-lifetime-deactivation-fn [lifetime fn])
  (handle-lifetime-deactivation [lifetime]))


(deftype Lifetime [^clojure.lang.Ref active? ;; Boolean
                   ^clojure.lang.Ref parent ;; Lifetime
                   ^clojure.lang.Ref children ;; []
                   ^clojure.lang.Ref on-lifetime-activation-fns ;; []
                   ^clojure.lang.Ref on-lifetime-deactivation-fns] ;; []
  ILifetime
  (add-lifetime-child [parent-lifetime child-lifetime]
    (assert (not (ensure (.parent child-lifetime))))
    (ref-set (.parent child-lifetime) parent-lifetime)
    (alter children conj child-lifetime)
    (when (ensure (.active? parent-lifetime))
      (handle-lifetime-activation child-lifetime))
    parent-lifetime)

  (remove-lifetime-child [parent-lifetime child-lifetime]
    (alter children disj child-lifetime)
    (when (ensure (.active? parent-lifetime))
      (handle-lifetime-deactivation child-lifetime))
    parent-lifetime)


  (add-lifetime-activation-fn [lifetime fn]
    (alter on-lifetime-activation-fns conj fn)
    lifetime)

  (add-lifetime-deactivation-fn [lifetime fn]
    (alter on-lifetime-deactivation-fns conj fn)
    lifetime)


  (handle-lifetime-activation [lifetime]
    (when-not (ensure active?)
      (ref-set active? true)
      ;; Iterate downwards in tree from LIFETIME; top-to-bottom; calling ON-LIFETIME-ACTIVATION-FNS as we go.
      (doseq [^clojure.lang.Fn f (ensure on-lifetime-activation-fns)]
        (f lifetime))
      (doseq [^Lifetime child-lifetime (ensure children)]
        (handle-lifetime-activation child-lifetime))
      lifetime))

  (handle-lifetime-deactivation [lifetime]
    (when (ensure active?)
      (ref-set active? false)
      ;; Iterate upwards from leaves of LIFETIME tree; bottom-to-top; calling ON-LIFETIME-DEACTIVATION as we go.
      (doseq [^Lifetime child-lifetime (ensure children)]
        (handle-lifetime-deactivation child-lifetime))
      (doseq [^clojure.lang.Fn f (ensure on-lifetime-deactivation-fns)]
        (f lifetime))
      lifetime)))


(defn mk-Lifetime []
  (Lifetime. (ref false) ;; ACTIVE?
             (ref false) ;; PARENT
             (ref [])    ;; CHILDREN
             (ref [])    ;; ON-LIFETIME-ACTIVATION-FNS
             (ref [])))  ;; ON-LIFETIME-DEACTIVATION-FNS


(defn activate-lifetime [^Lifetime lifetime]
  (handle-lifetime-activation lifetime))


(defn deactivate-lifetime [^Lifetime lifetime]
  (handle-lifetime-deactivation lifetime))







#_(try
  (dosync
   (let [root (mk-Lifetime)
         child-1 (mk-Lifetime)
         child-2 (mk-Lifetime)]
     (add-lifetime-activation-fn root (fn [root]
                                        (println "ROOT activated.")))
     (add-lifetime-activation-fn child-1 (fn [child-1]
                                           (println "CHILD-1 activated.")))
     (add-lifetime-activation-fn child-2 (fn [child-2]
                                           (println "CHILD-2 activated.")))
     (add-lifetime-deactivation-fn root (fn [root]
                                          (println "ROOT deactivated.")))
     (add-lifetime-deactivation-fn child-1 (fn [child-1]
                                             (println "CHILD-1 deactivated.")))
     (add-lifetime-deactivation-fn child-2 (fn [child-2]
                                             (println "CHILD-2 deactivated.")))
     (add-lifetime-child root child-1)
     (println "---")
     (activate-lifetime root)
     (println "---")
     (add-lifetime-child root child-2)
     (println "---")
     (deactivate-lifetime child-1)
     (println "---")
     (deactivate-lifetime root)))
  (catch Throwable e
    (dbg-prin1 e)))

;; ---
;; ROOT activated.
;; CHILD-1 activated.
;; ---
;; CHILD-2 activated.
;; ---
;; CHILD-1 deactivated.
;; ---
;; CHILD-2 deactivated.
;; ROOT deactivated.
