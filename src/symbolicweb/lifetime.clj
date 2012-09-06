(in-ns 'symbolicweb.core)

;;; Lifetime tracking
;;;;;;;;;;;;;;;;;;;;;
;;
;; Used to:
;;
;;   * Keep track of the lifetime (duration) of connections between Observables and Observers.
;;
;;   * Keep track of visibility in a UI context. E.g. when the user navigates away from a "page" the widgets all
;;     monitoring some Model switch from visible to non visible. Cleanup etc. might be needed on both client and server end.
;;
;;   * ..do many other things.



(defprotocol ILifetime
  (attach-lifetime [parent child]
    "Attaches CHILD Lifetime to the PARENT Lifetime.
If the parent is active, the CHILD and its children in turn will be made active too.")

  (detach-lifetime [lifetime]
    "Detaches LIFETIME from its parent Lifetime.
If LIFETIME is active it will be deactivated with all its children.")


  (add-lifetime-activation-fn [lifetime fn]
    "Adds a callback to run when LIFETIME is activated.")

  (add-lifetime-deactivation-fn [lifetime fn]
    "Adds a callback to run when LIFETIME is deactivated.")

  (do-lifetime-activation [lifetime])
  (do-lifetime-deactivation [lifetime]))



(deftype Lifetime [^clojure.lang.Ref active? ;; Boolean
                   ^clojure.lang.Ref parent ;; Lifetime
                   ^clojure.lang.Ref children ;; #{}
                   ^clojure.lang.Ref on-lifetime-activation-fns ;; []
                   ^clojure.lang.Ref on-lifetime-deactivation-fns] ;; []

  ILifetime
  (attach-lifetime [parent-lifetime child-lifetime]
    (assert (not (ensure (.parent child-lifetime)))
            (str child-lifetime " already has a parent: " (ensure (.parent child-lifetime))))
    (ref-set (.parent child-lifetime) parent-lifetime)
    (alter children conj child-lifetime)
    (when (ensure (.active? parent-lifetime))
      (do-lifetime-activation child-lifetime))
    parent-lifetime)


  (detach-lifetime [lifetime]
    (with (ensure parent)
      (assert it (str lifetime " isn't part of a Lifetime tree; it has no parent."))
      (assert (not= it ::stale-lifetime)
              (str lifetime " has already been detached from a Lifetime tree; it is stale."))
      (when-not (= it ::lifetime-root)
        (alter (.children it) disj lifetime)))
    (ref-set parent ::stale-lifetime)
    ;; Mark children and children of children stale also.
    (doseq [^Lifetime child-lifetime (ensure children)]
      (detach-lifetime child-lifetime))
    (do-lifetime-deactivation lifetime)
    lifetime)


  (add-lifetime-activation-fn [lifetime fn]
    (alter on-lifetime-activation-fns conj fn)
    lifetime)


  (add-lifetime-deactivation-fn [lifetime fn]
    (alter on-lifetime-deactivation-fns conj fn)
    lifetime)


  (do-lifetime-activation [lifetime]
    (when-not (ensure active?)
      (ref-set active? true)
      ;; Iterate downwards in tree (root at the top) from LIFETIME; top-to-bottom; calling ON-LIFETIME-ACTIVATION-FNS as we go.
      (doseq [^clojure.lang.Fn f (ensure on-lifetime-activation-fns)]
        (f lifetime))
      (doseq [^Lifetime child-lifetime (ensure children)]
        (do-lifetime-activation child-lifetime))
      lifetime))


  (do-lifetime-deactivation [lifetime]
    (when (ensure active?)
      (ref-set active? false)
      ;; Iterate upwards from leaves of LIFETIME tree (root at the top); bottom-to-top; calling ON-LIFETIME-DEACTIVATION-FNS
      ;; as we go.
      (doseq [^Lifetime child-lifetime (ensure children)]
        (do-lifetime-deactivation child-lifetime))
      (doseq [^clojure.lang.Fn f (ensure on-lifetime-deactivation-fns)]
        (f lifetime))
      lifetime)))




(defn mk-Lifetime []
  (Lifetime. (ref false) ;; ACTIVE?
             (ref false) ;; PARENT
             (ref #{})    ;; CHILDREN
             (ref [])    ;; ON-LIFETIME-ACTIVATION-FNS
             (ref [])))  ;; ON-LIFETIME-DEACTIVATION-FNS


(defn mk-LifetimeRoot []
  (with (mk-Lifetime)
    (ref-set (.parent it) ::lifetime-root)
    it))




(def -root-lifetime-
  (mk-Lifetime))



#_(try
  (dosync
   (let [root (mk-LifetimeRoot)
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
     (attach-lifetime root child-1)
     (println "---")
     (do-lifetime-activation root)
     (println "---")
     (attach-lifetime root child-2)
     (println "---")
     (detach-lifetime child-1)
     (println "---")
     (detach-lifetime root)))
  (catch Throwable e
    (clojure.stacktrace/print-stack-trace e)))

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
