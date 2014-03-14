(ns symbolicweb.examples.login
  (:use symbolicweb.core))



(def -login-salt- "1ea6560e-fff8-4e09-943d-37a55791f505")



(defmethod symbolicweb.examples.nostdal-org/mk-nostdal-org-viewport "/login" [^String uri request session]
  (let [root-widget (mk-bte :id "_body" :root-widget? true)
        viewport (mk-Viewport request session root-widget :page-title "SW: Login")
        email-io-vm (vm "")
        password-io-vm (vm "")
        email-vm (vm-sync email-io-vm nil #(strip-email %3))
        password-vm (with-observed-vms nil
                      (sha (str -login-salt- @email-vm @password-io-vm)))
        msg-vm (vm "")]
    (add-rest-head viewport
      "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />")
    (jqAppend root-widget
      (whc [:div]
        [:h1 "SymbolicWeb: Registration / login example"]
        [:p "This is registration and login &quot;in one&quot;. When using an email the system hasn't seen before, clicking the button will register a new user account. When using an email the system has seen before, clicking the button will login."]
        [:p "Email: " (sw (mk-TextInput email-io-vm :change :type 'email)) [:br]
         "Password: " (sw (mk-TextInput password-io-vm :change :type 'password)) [:br]
         (sw (set-event-handler "click" (whc [:button] "Register / login")
                                (fn [& _]
                                  (let [user-model (user-get-or-create @email-vm
                                                                       (fn [email]
                                                                         (with1 (mk-UserModelBase :email (vm email)
                                                                                                  :password (vm @password-vm))
                                                                           (db-put it "users"))))]
                                    (if (= @password-vm @(:password @user-model))
                                      (do
                                        (session-login session user-model viewport "permanent" (fn []))
                                        (vm-set msg-vm "Login success."))
                                      (vm-set msg-vm "Login failed. Wrong password?"))))))]
        [:p "Logged in: " (sw (mk-b (spget session :logged-in?)))]
        (sw (with1 (set-event-handler "click" (whc [:button] "Logout")
                                      (fn [& _]
                                        (session-logout session)
                                        (vm-set msg-vm "Logged out.")))
              (vm-observe (spget session :logged-in?) (.lifetime it) true
                          #(if %3
                             (jqCSS it "display" "")
                             (jqCSS it "display" "none")))))
        [:p "Message: " (sw (mk-span msg-vm))]))

    viewport))
