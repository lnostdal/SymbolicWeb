(in-ns 'symbolicweb.core)

;;; Lifetime tracking
;;;;;;;;;;;;;;;;;;;;;
;;
;; Used to:
;;
;;   * Keep track of the lifetime (duration) of connections between Observables and their observers.
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


  (add-lifetime-activation-fn [lifetime f]
    "Adds a callback to run when LIFETIME is activated.
  F: (fn [lifetime] ..)")

  (add-lifetime-deactivation-fn [lifetime f]
    "Adds a callback to run when LIFETIME is deactivated.
  F: (fn [lifetime] ..)")

  (do-lifetime-activation [lifetime])
  (do-lifetime-deactivation [lifetime])

  (lifetime-state-of [lifetime])
  (lifetime-parent-of [lifetime])
  (lifetime-children-of [lifetime]))



(deftype Lifetime [^Ref state ;; :initial --> :member-of-tree --> :activated --> :deactivated
                   ^Ref parent ;; nil, Lifetime or :lifetime-root
                   ^Ref children ;; []
                   ^Ref on-lifetime-activation-fns ;; []
                   ^Ref on-lifetime-deactivation-fns] ;; []

  ILifetime
  (lifetime-state-of [_] (ensure state))
  (lifetime-parent-of [_] (ensure parent))
  (lifetime-children-of [_] (ensure children))



  (attach-lifetime [parent child]
    (case (lifetime-state-of parent)
      (:initial :member-of-tree :activated)
      (do
        (assert (= :initial (lifetime-state-of child))
                (str child " in invalid state: " (lifetime-state-of child)))
        (ref-set (.state child) :member-of-tree)
        (ref-set (.parent child) parent)
        (alter children conj child)
        (when (= :activated (lifetime-state-of parent))
          (do-lifetime-activation child))))
    parent)



  (detach-lifetime [lifetime]
    (case (lifetime-state-of lifetime)
      (:member-of-tree :activated)
      (do
        (when (= :activated (lifetime-state-of lifetime))
          (do-lifetime-deactivation lifetime))
        (when-not (= :lifetime-root (lifetime-parent-of lifetime))
          (alter (.children (lifetime-parent-of lifetime)) (partial filterv #(not= % lifetime)))))

      nil)
    lifetime)



  (add-lifetime-activation-fn [lifetime f]
    (case (lifetime-state-of lifetime)
      (:initial :member-of-tree)
      (alter on-lifetime-activation-fns conj f)

      :activated
      (f lifetime))
    lifetime)



  (add-lifetime-deactivation-fn [lifetime f]
    (case (lifetime-state-of lifetime)
      (:initial :member-of-tree :activated)
      (alter on-lifetime-deactivation-fns conj f)

      :deactivated
      (f lifetime))
    lifetime)



  (do-lifetime-activation [lifetime]
    (case (lifetime-state-of lifetime)
      :member-of-tree
      (do
        (ref-set state :activated)
        ;; Root and down to leaves (bottom).
        (doseq [^Fn f (ensure on-lifetime-activation-fns)]
          (f lifetime))
        (doseq [^Lifetime child (lifetime-children-of lifetime)]
          (do-lifetime-activation child))))
    lifetime)



  (do-lifetime-deactivation [lifetime]
    ;; Leaves and up to root (top).
    (doseq [^Lifetime child-lifetime (lifetime-children-of lifetime)]
      (do-lifetime-deactivation child-lifetime))
    (case (lifetime-state-of lifetime)
      :activated
      (do
        (ref-set state :deactivated)
        (doseq [^Fn f (ensure on-lifetime-deactivation-fns)]
          (f lifetime)))

      nil)
    lifetime))



(defn mk-Lifetime ^Lifetime []
  (Lifetime. (ref :initial) ;; STATE
             (ref nil)      ;; PARENT
             (ref [])       ;; CHILDREN
             (ref [])       ;; ON-LIFETIME-ACTIVATION-FNS
             (ref [])))     ;; ON-LIFETIME-DEACTIVATION-FNS



(defn mk-LifetimeRoot ^Lifetime []
  "NOTE: This still needs to be activated."
  (with (mk-Lifetime)
    (ref-set (.parent it) :lifetime-root)
    (ref-set (.state it) :member-of-tree)
    it))




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
