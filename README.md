# SymbolicWeb

AJAX long poll/Comet/ReverseHTTP/WebSockets/whatever Web UI (WUI?) thing written in Clojure.

SW was originally written in Common Lisp, and that version is still found at this location albeit unmaintained:

  https://github.com/lnostdal/old-SymbolicWeb



## Features and/or goals

[Push](http://en.wikipedia.org/wiki/Dataflow)- instead of pull-oriented architecture: http://sw.nostdal.org/dataflow


Multi-paradigm; does not try to shoehorn everything into a single paradigm.


Somewhat high-level, but does not hide, restrict or remove access to low-level stuff.


Built [bottom-up](http://en.wikipedia.org/wiki/Top-down_and_bottom-up_design#Computer_science) on existing software commodity
components like [Netty](http://netty.io/), [PostgreSQL](http://www.postgresql.org/) and [more](https://github.com/lnostdal/SymbolicWeb/blob/master/project.clj).

Simple (DAO mapping)[http://en.wikipedia.org/wiki/Data_access_object] with a read cache, but does not pretend that anything like this is good enough for all cases.

While adding in traditional web routing is possible, SW doesn't do any web routing itself. It instead provides
two-way mapping to/from server state from/to client URL (query string) via the HTML5 History API:
  * http://sw.nostdal.org/history?a=3&b=4
  * http://sw.nostdal.org/history?a=40&b=2


Does not have a strict, exclusive GUI- ([Widgets](http://en.wikipedia.org/wiki/GUI_widget)) or Web (HTML/CSS/JS) -oriented
approach to "all things", but instead mixes or supports both ways of dealing with user interfaces at any time.


Simple (DAO mapping)[http://en.wikipedia.org/wiki/Data_access_object] with a read cache, but does not pretend that anything like
this is good enough for all cases.


Database and memory transactions (Clojure STM) work together via [two-phase committing](http://en.wikipedia.org/wiki/Two-phase_commit_protocol).



## Status

Some interesting features, but not API stable -- and not many features or very *complete* yet. The project has little
documentation (not enough time..), but there is some example code here:

  https://github.com/lnostdal/SymbolicWeb/tree/master/src/symbolicweb/examples


It runs the work-in-progress e-commerce platform Free or Deal. Some buzzwords: "shopping, gamification and social networking
mixed".

  http://freeordeal.no/ (Norwegian only, for now)


The least stable things API-wise is probably the DB related stuff.

Developed and tested on Xubuntu 12.04 using Google Chrome with SW running on Oracle JDK 7:

  http://www.webupd8.org/2012/01/install-oracle-java-jdk-7-in-ubuntu-via.html


..further *basic* testing has been done on:

  * Android phones: 2.2, 2.3, 4.0, 4.1, 4.2
  * Android tablets: 4.0, 4.1
  * iOS phones: iOS 3 (iPhone 3GS), iOS 4 (iPhone 4)
  * iOS tablets: iOS 3.2 (iPad), iOS 4.3.2 (iPad 2), iOS 5.0 (iPad 2 5.0)
  * Windows 8: IE10, IE9 (via IE10), IE8 (via IE10), Firefox 18.x
  * Windows 7: IE8, IE9, Safari 4.0, Safari 5.0, Safari 5.1
  * Windows XP: IE7, IE8
  * Linux (Xubuntu 12.04): Chrome 24.x, Firefox 18.x


Things are known not to work on:

  * iOS 6.x (iPhone 4S 6.0, iPhone 5)



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
