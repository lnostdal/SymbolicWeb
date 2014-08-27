(ns symbolicweb.examples.login
  (:use symbolicweb.core))



(def -login-salt- "1ea6560e-fff8-4e09-943d-37a55791f505")



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/login" [^String uri request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SW: Login")
        email-io-model (vm "")
        email-io-view (mk-TextInput email-io-model :change :type 'email)
        password-io-model (vm "")
        password-io-view (mk-TextInput password-io-model :change :type 'password)
        email-vm (vm-sync email-io-model nil #(strip-email %3))
        password-vm (with-observed-vms nil
                      (sha (str -login-salt- @email-vm @password-io-model)))
        msg-vm (vm "")]
    (add-rest-head viewport
      "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />")
    (jqAppend root-widget
      (with1 (whc [:form]
               [:h1 "SymbolicWeb: Registration / login example"]
               [:p "This is registration and login &quot;in one&quot;. When using an email the system hasn't seen before, clicking the button will register a new user account. When using an email the system has seen before, clicking the button will login."]

               [:p "Email: " (sw email-io-view) [:br]
                "Password: " (sw password-io-view) [:br]
                [:button {:type "submit"} "Register / login"]]

               [:p "Logged in: " (sw (mk-b (spget session :logged-in?)))]
               (sw (with1 (whc [:button] "Logout")
                     (set-event-handler "click" it
                                        (fn [& _]
                                          (session-logout session)
                                          (vm-set msg-vm "Logged out."))
                                        :js-after "event.preventDefault();") ;; Don't trigger form submit.
                     (vm-observe (spget session :logged-in?) (.lifetime it) true
                                 #(jqCSS it "display" (if %3 "" "none")))))
               [:p "Message: " (sw (mk-span msg-vm))])
        (set-event-handler "submit" it
                           (fn [& _]
                             (let [user-model (user-get-or-create @email-vm
                                                                  (fn [email]
                                                                    (with1 (mk-UserModelBase :email (vm email)
                                                                                             :password (vm @password-vm))
                                                                      (dao-put it "users"))))]
                               (if (= @password-vm @(:password @user-model))
                                 (session-login session user-model viewport "permanent"
                                                (fn [] (vm-set msg-vm "Login success.")))
                                 (vm-set msg-vm "Login failed. Wrong password?"))))
                           ;; Issue #35: https://github.com/lnostdal/SymbolicWeb/issues/35
                           :js-before (str "$('#" (.id email-io-view) "').trigger('change');"
                                           "$('#" (.id password-io-view) "').trigger('change');"
                                           "return(true);")
                           :js-after "event.preventDefault();"))) ;; Don't trigger form submit.

    viewport))
