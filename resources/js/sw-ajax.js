"use strict"; // http://ejohn.org/blog/ecmascript-5-strict-mode-json-and-more/



/// swHandleError ///
/////////////////////

function swHandleError(){
  try{
    if(console) // Not using shitty IE browser?
      console.error(arguments);
    swAjax("&do=error&msg=" + encodeURIComponent(JSON.stringify(arguments, null, 2)));
  }
  catch(e){
    return(true); // Can't do anything reasonable here; don't let default handler run.
  }
  return(false); // Let default handler run.
}

window.onerror = swHandleError;



/// swAddOnLoadFN ///
/////////////////////

var swAddOnLoadFN, swDoOnLoadFNs;
(function (){
   var funs = new Array();

   swAddOnLoadFN = function(fun){
     funs.push(fun);
   };

   swDoOnLoadFNs = function(){
     for(var fun in funs){
       try {
         funs[fun]();
       }
       catch(err){
         console.error("swDoOnLoadFNs:\n" + funs[fun]);
         console.error(err);
       }
     }
   };
 })();



/// swURL ///
/////////////

function swURL(params){
  return([window.location.protocol, "//",
          window.location.host,
          window.location.pathname,
          "?_sw_viewport_id=", _sw_viewport_id,
          params.join("")
         ].join(""));
}



/// swAjax ///
//////////////

var swAjax =
  (function(){
     var queue = new Array();
     var spinner = false;

     function prepareSpinner(){
       if(!spinner){
         spinner = setTimeout(function(){ $("body").css("cursor", "wait"); }, 500);
       }
     }

    function cancelSpinner(){
      if(spinner){
        clearTimeout(spinner);
        spinner = false;
        $("body").css("cursor", "auto");
      }
    }

     function handleRestOfQueue(){
       queue.shift();
       if(queue.length != 0)
         queue[0]();
       else{
         cancelSpinner();
       }
     }

     return function(params, callback_data, after_fn){
       if(queue.push(function(){
                       var url = swURL(["&_sw_request_type=ajax", params]);
                       var options = {
                         type: "POST",
                         url: url,
                         data: callback_data,
                         dataType: "script",

                         beforeSend: prepareSpinner,
                         complete: function(){
                           if(after_fn) after_fn();
                           handleRestOfQueue();
                         }
                       };

                       $.ajax(options);
                     }) == 1) // if()..
         queue[0]();
     };
   })();



/// swComet ///
///////////////

var _sw_comet_response = false;
var _sw_comet_last_response_ts = new Date().getTime();

var swComet  =
  (function(){
     function callback(){
       if(_sw_comet_response){
         _sw_comet_last_response_ts = new Date().getTime();
         _sw_comet_response = false;
         swComet("&do=ack");
       }
       else{
         setTimeout("swComet('&do=timeout');", 1000);
       }
     }

     function doIt(params){
       $.ajax({type: "POST",
               url: swURL(["&_sw_request_type=comet", params]),
               dataType: "script",
               complete: callback})
       .fail(function(jq_xhr, settings, exception){
         swHandleError(exception.stack);
       });
     }
     // Stops "throbbing of doom" and ensure we do not recurse until a stack overflow.
     return function(params){ setTimeout(function(){ doIt(params); }, 0); };
   })();



/// swWidgetEvent ///
////////////////////

function swWidgetEvent(widget_id, callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=widget-event" + "&_sw_widget-id=" + widget_id + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



/// swViewportEvent ///
///////////////////////

function swViewportEvent(callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=viewport-event" + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



/// Shutdown ///
/////////////////

// So unload event fires on (some, but still not all ..sigh) navigation in Opera too.
if(typeof(opera) != "undefined"){
  opera.setOverrideHistoryNavigationMode("compatible");
  history.navigationMode = "compatible";
}

// Eager, instead of based on timeout -- GC of the server side Viewport object and all its Widgets (and their incomming
// connections from the Model/DB) on page unload.
$(window).on("unload", function(){
  if(typeof(_sw_viewport_id) != "undefined")
    $.ajax({type: "POST",
            url: swURL(["&_sw_request_type=ajax", ["&do=unload"]]),
            async: false});
});



/// Boot! ///
/////////////

function swBoot(){
  if(true){ // TODO: Windows 8 and IE10 is buggy as hell.. //document.cookie.indexOf(sw_cookie_name) != -1){
    // At this point pre-boot and all context (variables etc) is good to go so we connect our background channel..
    swComet("&do=boot");
    // ..and set up something that'll ensure the channel stays alive
    // when faced with JS dying after computer waking up from suspend etc..
    var sw_mouse_poll_ts = new Date().getTime();
    var sw_mouse_poll_interval_ms = 5000;
    var sw_comet_timeout_window_ms = 5000; // Response time window after long poll timeout.
    // TODO: onfocus too?
    $(document).on("mousemove", function(e){
      var ts = new Date().getTime();
      if((ts - sw_mouse_poll_ts) > sw_mouse_poll_interval_ms){
        sw_mouse_poll_ts = ts;
        if((ts - _sw_comet_last_response_ts) > (_sw_comet_timeout_ts + sw_comet_timeout_window_ms)){
          console.log("SymbolicWeb: Client connection JS-loop has died: rebooting...");
          window.location.href = window.location.href;
        }
      }
    });
  }
  else{
    console.error("SymbolicWeb: Cookies must be enabled.");
  }
}
