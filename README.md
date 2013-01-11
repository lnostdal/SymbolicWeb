# SymbolicWeb

AJAX long poll/Comet/ReverseHTTP/WebSockets/whatever Web UI (WUI) thing written in Clojure.

SW was originally written in Common Lisp, and that version is still found at this location albeit unmaintained:

  https://github.com/lnostdal/old-SymbolicWeb



## Status

Core functionality is stable (if one ignore some of the DB stuff), but not many features yet.
It runs the work-in-progress e-commerce platform Free or Deal. Some buzzwords: "shopping, gamification and social networking
mixed".

  http://freeordeal.no/ (Norwegian only, for now)


The least stable things API-wise is probably the DB related stuff.



## Usage

*This is very out of date; if you really want to try this just get in touch instead for now.*


Lighttpd 1.5.x (from git) is used (recommended) for the boring static content. To enable serving from port 80 we do:

    sudo setcap 'cap_net_bind_service=+ep' /usr/local/sbin/lighttpd


..then we start it:

    /usr/local/sbin/lighttpd -f ~/clojure/src/symbolicweb/resources/lighttpd.conf


Start swank in the SW directory:

    ~/clojure/src/symbolicweb$ lein swank


Start Emacs and connect to the now running swank using Slime, then make sure everything's loaded:

    user> (require 'symbolicweb.core)


Now start SW:

    user> (in-ns 'symbolicweb.core)
    symbolicweb.core> (-main)


Direct your browser to http://localhost.nostdal.org/empty-page/sw (yes, this will really resolve to 127.0.0.1). To send JS to the
browser, try:

    symbolicweb.core> (dosync (alert "Hi!"))




## Style

130 column width using a 1920 or 1600 pixel wide screen should give room for two columns while still being readable:

|---------------------------------------------------------------------------------------------------------------------------------|


* ThisIsAType   (deftype, defrecord, defprotocol, etc..)
* mk-SomeType   (a "helper constructor" for some type)
* this-is-a-variable
* this-is-a-function
* -this-is-a-global-variable-
* *this-is-a-global-dynamic-variable*
* Fns that might be handy to denote using the #() macro should have a particular ordering with regards to their parameter lists:

    (fn [least-commonly-used-parameter most-commonly-used-parameter] ..)


  This because e.g. (#(vector %2 %2) "sometimes used" "always used") will always work,
  while (#(vector %1 %1) "always used" "sometimes used") might fail.


Naming of Container (container_model.clj and container_model_node.clj) related functions follow or match the naming
found in jQuery. E.g., append, prepend, before and after:

* http://api.jquery.com/category/manipulation/dom-insertion-inside/
* http://api.jquery.com/category/manipulation/dom-insertion-outside/




## Terminology and abbreviations

* MTX: Memory transaction; e.g. DOSYNC (STM).
* DBTX: Database transaction; e.g. WITH-SW-DB.
* 2PC: Two-phase transaction; database then memory transaction; e.g. SWSYNC, or WITH-SW-DB with its HOLDING-TRANSACTION callback
  made use of. http://en.wikipedia.org/wiki/Two-phase_commit_protocol
* CTX: Context.



## License

Copyright (C) 2005 - 2011, 2012, 2013 Lars Rune Nøstdal

Distributed under the GNU Affero General Public License (for now):

* http://en.wikipedia.org/wiki/Affero_General_Public_License
* http://www.gnu.org/licenses/agpl.html

Some sort of CLA is implied:

* http://en.wikipedia.org/wiki/Contributor_License_Agreement
