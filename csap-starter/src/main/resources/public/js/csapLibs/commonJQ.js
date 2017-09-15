/**
 * 
 * Jquery based helpers functions
 * 
 */

$.ajaxSetup( {
	 cache: false,
	 traditional: true
// traditional is critical for servlet based param processing.
// timeout : deployTimeout, never timeout
} );

function jsonToString(jsonObject) {
	 return JSON.stringify( jsonObject, null, "\t" )
}

function numberWithCommas(x) {
	 return x.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
}

function csapRotate($selector, rotateLimit, direction, reset) {

	 if ( direction == undefined )
		  direction = "rotateY"; // rotateX

	 var limit = Math.abs( rotateLimit );

	 var iteration = 0;
	 var intervalJob = setInterval( function () {
		  var degreesToRotate = iteration;
		  if ( rotateLimit < 0 ) degreesToRotate = -degreesToRotate;
		  
		  if ( iteration++ > limit ) {
				clearInterval( intervalJob );
				if ( reset !== undefined && reset ) {
					 csapRotate($selector, -rotateLimit, direction, reset)
					 return ;
				}
		  }

		  $selector.css( {
				'-moz-transform': direction + '(' + degreesToRotate + 'deg)',
				'-webkit-transform': direction + '(' + degreesToRotate + 'deg)',
				'-o-transform': direction + '(' + degreesToRotate + 'deg)',
				'-ms-transform': direction + '(' + degreesToRotate + 'deg)',
				'transform': direction + '(' + degreesToRotate + 'deg)'
		  } );

	 }, 10 );
}


//eg. numberOfSamples becomes number Of Samples
function splitUpperCase(key) {

	 var resultWithSpaces = ""; // lowercase first word
	 for ( var i = 0; i < key.length; i++ ) {
		  if ( key.charAt( i ) == "_" )
				continue; // strip underscore
//		if ( i > 0 && key.charAt(i-1) == key.charAt(i-1).toUpperCase() ) {
//			// do not space consecutiive upper let
//		}


		  if ( i > 0 && (key.charAt( i ).toUpperCase() == key.charAt( i )) ) {

				// hack for OS
				if ( key.charAt( i ) != "S" && key.charAt( i - 1 ) != "0"
						  && (!(key.charAt( i ) >= 0 && key.charAt( i ) <= 9)) )
					 resultWithSpaces += " ";
		  }
		  resultWithSpaces += key.charAt( i );

	 }
	 return resultWithSpaces;
}

function handleConnectionError(command, errorThrown) {
	
	if ( errorThrown == "abort") {
		console.log("Request was aborted: " + command) ;
		return ;
	}
	 var message = "Failed connecting to server - this is most ofter triggered by SSO credential expiring.";
	 message += "<br><br>Command: " + command
	 message += '<br><br>Server Response:<pre class="error" >' + errorThrown + "</pre>";

	 var errorDialog = alertify.confirm( message );

	 errorDialog.setting( {
		  title: "Host Connection Error",
		  resizable: false,
		  'labels': {
				ok: 'Reload Page',
				cancel: 'Ignore Error'
		  },
		  'onok': function () {
				document.location.reload( true );
		  },
		  'oncancel': function () {
				alertify.warning( "Wait a few seconds and try again," );
		  }

	 } );

	 $( 'body' ).css( 'cursor', 'default' );
}


var CSAP_THEME_COLORS = ["#5DA5DA", "#FAA43A", "#60BD68", "#F17CB0", "#B2912F", "#B276B2", "#DECF3F", "#F15854", "#4D4D4D"];
var CSAP_BAR_COLORS = ["#BFD8F5", "#F5DCBF", "#BFF5DA"];

//var CSAP_THEME_COLORS = [ "#C2D985", "#E3675C", "#FFDE89", "#B1DDF3", "#F1B2E1",
//               			"#827259", "#9D9B86", "#1C5CAB", "#006600", "#E000E0", "#667FFF",
//               			"#DCCCAA", "#ECC87A","#FF8651" ];

// jqplot needs arrays same size
function padMissingPointsInArray(arrayContainer) {

	 var longestArray = null;
	 for ( var i = 0; i < arrayContainer.length; i++ ) {

		  var curArray = arrayContainer[ i ];
		  if ( longestArray == null || longestArray.length < curArray.length )
				longestArray = curArray;
	 }

	 for ( var i = 0; i < arrayContainer.length; i++ ) {

		  var curArray = arrayContainer[ i ];
		  if ( longestArray.length == curArray.length )
				continue;

		  // make them the same size.
		  for ( var j = 0; j < longestArray.length; j++ ) {
				var longestPoint = longestArray[ j ];
				var curPoint = curArray[ j ];
				//console.log("padd handler") ;
				if ( curPoint == undefined || longestPoint[0] != curPoint[0] ) {

					 var padPoint = [longestPoint[0], 0];

					 curArray.splice( j, 0, padPoint );
				}
		  }

	 }

}

// Adding Methods to the Date Object to handle Daylight savings

Date.prototype.stdTimezoneOffset = function () {
	 var jan = new Date( this.getFullYear(), 0, 1 );
	 var jul = new Date( this.getFullYear(), 6, 1 );
	 return Math.max( jan.getTimezoneOffset(), jul.getTimezoneOffset() );
}

Date.prototype.dst = function () {
	 return this.getTimezoneOffset() < this.stdTimezoneOffset();
}

Date.prototype.addMinutes = function (m) {
	 this.setMinutes( this.getMinutes() + m );
	 return this;
}

var dateFormat = function () {
	 var token = /d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\1?|[LloSZ]|"[^"]*"|'[^']*'/g,
				timezone = /\b(?:[PMCEA][SDP]T|(?:Pacific|Mountain|Central|Eastern|Atlantic) (?:Standard|Daylight|Prevailing) Time|(?:GMT|UTC)(?:[-+]\d{4})?)\b/g,
				timezoneClip = /[^-+\dA-Z]/g,
				pad = function (val, len) {
					 val = String( val );
					 len = len || 2;
					 while ( val.length < len )
						  val = "0" + val;
					 return val;
				};

	 // Regexes and supporting functions are cached through closure
	 return function (date, mask, utc) {
		  var dF = dateFormat;

		  // You can't provide utc if you skip other args (use the "UTC:" mask prefix)
		  if ( arguments.length == 1 && Object.prototype.toString.call( date ) == "[object String]" && !/\d/.test( date ) ) {
				mask = date;
				date = undefined;
		  }

		  // Passing date through Date applies Date.parse, if necessary
		  date = date ? new Date( date ) : new Date;
		  if ( isNaN( date ) )
				throw SyntaxError( "invalid date" );

		  mask = String( dF.masks[mask] || mask || dF.masks["default"] );

		  // Allow setting the utc argument via the mask
		  if ( mask.slice( 0, 4 ) == "UTC:" ) {
				mask = mask.slice( 4 );
				utc = true;
		  }

		  var _ = utc ? "getUTC" : "get",
					 d = date[_ + "Date"](),
					 D = date[_ + "Day"](),
					 m = date[_ + "Month"](),
					 y = date[_ + "FullYear"](),
					 H = date[_ + "Hours"](),
					 M = date[_ + "Minutes"](),
					 s = date[_ + "Seconds"](),
					 L = date[_ + "Milliseconds"](),
					 o = utc ? 0 : date.getTimezoneOffset(),
					 flags = {
						  d: d,
						  dd: pad( d ),
						  ddd: dF.i18n.dayNames[D],
						  dddd: dF.i18n.dayNames[D + 7],
						  m: m + 1,
						  mm: pad( m + 1 ),
						  mmm: dF.i18n.monthNames[m],
						  mmmm: dF.i18n.monthNames[m + 12],
						  yy: String( y ).slice( 2 ),
						  yyyy: y,
						  h: H % 12 || 12,
						  hh: pad( H % 12 || 12 ),
						  H: H,
						  HH: pad( H ),
						  M: M,
						  MM: pad( M ),
						  s: s,
						  ss: pad( s ),
						  l: pad( L, 3 ),
						  L: pad( L > 99 ? Math.round( L / 10 ) : L ),
						  t: H < 12 ? "a" : "p",
						  tt: H < 12 ? "am" : "pm",
						  T: H < 12 ? "A" : "P",
						  TT: H < 12 ? "AM" : "PM",
						  Z: utc ? "UTC" : (String( date ).match( timezone ) || [""]).pop().replace( timezoneClip, "" ),
						  o: (o > 0 ? "-" : "+") + pad( Math.floor( Math.abs( o ) / 60 ) * 100 + Math.abs( o ) % 60, 4 ),
						  S: ["th", "st", "nd", "rd"][d % 10 > 3 ? 0 : (d % 100 - d % 10 != 10) * d % 10]
					 };

		  return mask.replace( token, function ($0) {
				return $0 in flags ? flags[$0] : $0.slice( 1, $0.length - 1 );
		  } );
	 };
}();

// Some common format strings
dateFormat.masks = {
	 "default": "ddd mmm dd yyyy HH:MM:ss",
	 shortDate: "m/d/yy",
	 mediumDate: "mmm d, yyyy",
	 longDate: "mmmm d, yyyy",
	 fullDate: "dddd, mmmm d, yyyy",
	 shortTime: "h:MM TT",
	 mediumTime: "h:MM:ss TT",
	 longTime: "h:MM:ss TT Z",
	 isoDate: "yyyy-mm-dd",
	 isoTime: "HH:MM:ss",
	 isoDateTime: "yyyy-mm-dd'T'HH:MM:ss",
	 isoUtcDateTime: "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'"
};

// Internationalization strings
dateFormat.i18n = {
	 dayNames: [
		  "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
		  "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
	 ],
	 monthNames: [
		  "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
		  "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
	 ]
};

// For convenience...
Date.prototype.format = function (mask, utc) {
	 return dateFormat( this, mask, utc );
};

/**
 * Considerations:
 * - all graphs assume us data centers in central time:  CST: -360
 * - java script concept of timezones is very limited
 * - Users browsing by date are likely wanting to use dates from log files
 * 
 * - selectedTime is always 00:00 of the localtime
 */
function calculateUsCentralDays(selectedTime) {

	 var nowDate = new Date( Date.now() );
	 var selectedDate = new Date( selectedTime )

	 var deltaDays = Math.floor( (nowDate.getTime() - selectedDate.getTime()) / 24 / 60 / 60 / 1000 );
	 // var days=Math.round((nowDate.getTime() - selectedDate.getTime()  )/24/60/60/1000) ;
	 // console.log("Days: " + deltaDays + " selectedDate: " + selectedDate + " nowDate: " + nowDate) ;
	 return deltaDays;
}
//function calculateUsCentralDays( selectedTime )  {
//	var nowDate=new Date(Date.now());
//	var selectedUsOffset = 5 * 60 * 60 * 1000 ; // 5 hours in milliseconds
//	var selectedDate = new Date ( selectedTime + selectedUsOffset )  ;
//
//	var nowUtc = new Date(nowDate.getUTCFullYear(), nowDate.getUTCMonth(), 
//			nowDate.getUTCDate(),  nowDate.getUTCHours() , nowDate.getUTCMinutes(), nowDate.getUTCSeconds());
//	
//	nowUtc.setTime( nowUtc.getTime()  - selectedUsOffset ) ;
//	console.log( "nowDate.getUTCHours(): " + nowDate.getUTCHours() + "    nowUtc: " + nowUtc  + "    nowDate: " + nowDate)  ;
//
//	// console.log("Timezone offset is: " + selectedDate.getTimezoneOffset() ) ;
////	console.log("nowCentralOffset: " + (nowCentralOffset/60000) + "\n selectedDate: " + selectedDate 
////			+ "\n nowDate: " + nowDate + "               nowUtc: " + nowUtc ) ;
////	
//	var days=Math.round(
//            (nowUtc.getTime() - selectedDate.getTime()  )/24/60/60/1000) ;
//	return days ;
//}

function getUsCentralTime(localTime) {
	 var usCentral = new Date( localTime );
	 usCentral.setMinutes( usCentral.getMinutes() + localTime.getTimezoneOffset() - 360 );
	 return usCentral;
}
//var localTime = new Date();
//var remoteTime = new Date();
//remoteTime.setMinutes(remoteTime.getMinutes() + localTime.getTimezoneOffset() +930);
//console.log( "localTime:" + localTime + "localTime.getTimezoneOffset(): " + localTime.getTimezoneOffset() + " remoteTime:" + remoteTime) ; 



// Sorting html selects. Default sorts based on text value of option, but param can be passed
//
$.fn.sortSelect = function (sortByAttribute) {

	 sortByAttribute = typeof sortByAttribute !== 'undefined' ? sortByAttribute : "text";
	 // Get options from select box
	 var my_options = $( "option", this );

	 var selectedText = $( "option:selected", this ).text();
	 //console.log("sortSelect selectedText: " + selectedText) ;
	 // sort alphabetically
	 my_options.sort( function (a, b) {

		  var aVal = a.text;
		  bVal = b.text;
		  if ( sortByAttribute != "text" ) {
				aVal = a.getAttribute( sortByAttribute );
				bVal = b.getAttribute( sortByAttribute );
		  }
		  if ( aVal.toLowerCase() > bVal.toLowerCase() )
				return 1;
		  else if ( aVal.toLowerCase() < bVal.toLowerCase() )
				return -1;
		  else
				return 0
	 } )
	 //replace with sorted my_options, preserving selected text
	 $( this ).empty().append( my_options );

	 $( "option", this ).each( function (index) {
		  // console.log("Re-Checking: " + $(this).text() + " against: " +  selectedText) ;
		  if ( $( this ).text() == selectedText ) {
				//console.log("Re-Selecting: " + selectedText) ;
				$( this ).prop( "selected", "selected" );
		  }
	 } );
}


function executionTime(callback) {
	 var start, done;
	 start = new Date();
	 callback.call();
	 done = new Date();
	 return done.getTime() - start.getTime();
}

/**
 * Hook to leave console lines in code for debugging. Use sparingly though
 * 
 */
if ( !window.console ) {

	 (function () {
		  var names = ["log", "debug", "info", "warn", "error", "assert", "dir",
				"dirxml", "group", "groupEnd", "time", "timeEnd", "count",
				"trace", "profile", "profileEnd"], i, l = names.length;

		  window.console = {};

		  for ( i = 0; i < l; i++ ) {
				window.console[names[i]] = function () {
				};
		  }
	 }());

}

/**
 * Check whether a JQ object has a scrollbar.
 * 
 */
(function ($) {
	 $.fn.hasScrollBar = function () {
		  return this.get( 0 ).scrollHeight > this.height();
	 };
})( jQuery );

/**
 * get a param: if ( $.urlParam("nav") != null) navUrl=$.urlParam("nav") ;
 */
$.urlParam = function (name) {
	 var results = new RegExp( '[\\?&]' + name + '=([^&#]*)' )
				.exec( window.location.href );
	 if ( results == null ) {
		  return null;
	 }
	 return results[1] || 0;
};

/**
 * Get the column from the row
 * 
 */

$.fn.column = function (i) {
	 return $( 'td:nth-child(' + (i + 1) + ')', this );
};

/**
 * Helper for displaying js object type
 * 
 * @param obj
 * @returns
 */
function type(obj) {
	 return Object.prototype.toString.call( obj ).match( /^\[object (.*)\]$/ )[1];
}

/**
 * IE chokes on some chars
 */
function openWindowSafely(url, windowFrameName) {

	 // console.log("window frame name: " + getValidWinName( windowFrameName) + "
	 // url: " + encodeURI(url)
	 // + " encodeURIComponent:" + encodeURIComponent(url)) ;

	 window.open( encodeURI( url ), getValidWinName( windowFrameName ) );

}

function getValidWinName(inputName) {
	 var regex = new RegExp( "-", "g" );
	 var validWindowName = inputName.replace( regex, "" );

	 regex = new RegExp( " ", "g" );
	 validWindowName = validWindowName.replace( regex, "" );

	 return validWindowName;
}

// alert("browser is: " + $.browser.chrome ) ;
/**
 * hook for chrome which struggles with posting to multiple targets only chrome
 * will use winIndex*ms to delay launch
 * 
 */
function postAndRemove(windowFrameName, urlAction, inputMap) {

	 var $form = $( '<form id="temp_form"></form>' );

	 $form.attr( "action", urlAction );
	 $form.attr( "method", "post" );
	 $form.attr( "target", getValidWinName( windowFrameName ) );

	 $.each( inputMap, function (k, v) {
		  $form.append( '<input type="hidden" name="' + k + '" value="' + v
					 + '"/>' );
	 } );

	 // window.open(urlAction, host + serviceInstance + "Logs");
	 // alert( $form.clone().wrap('<p>').parent().html() );
	 // alert("form is :" + $form.html() ) ;
	 $( "body" ).append( $form );

	 // console.log( "getValidWinName(windowFrameName)" +
	 // getValidWinName(windowFrameName) + " \n Hidden Form is: " + $form.html()
	 // ) ;
	 // return;

	 // hook for chrome not liking mult-wins opened
	 // if ( $.browser.chrome ) {
	 // //alert ("chrome") ;
	 // setTimeout( function() {
	 // $form.submit();
	 // $form.remove() ;
	 //			
	 // }, winIndex*500);
	 // } else {
	 $form.submit();
	 $form.remove();
	 // }
}

// function postAndRemove( windowFrameName, urlAction, inputMap , winIndex,
// isMulti) {
//	
// var multi="" ;
// if ( isMulti != undefined && isMulti ) {
// multi='enctype="multipart/form-data"' ;
// }
// var $form = $('<form id="temp" ' + multi + '></form>');
//	
// $form.attr("action", urlAction) ;
// $form.attr("method", "post");
// $form.attr("target", getValidWinName(windowFrameName) ) ;
//	
// $.each(inputMap, function(k, v) {
// $form.append('<input type="hidden" name="' + k + '" value="' + v
// + '"/>');
// });
//
// //window.open(urlAction, host + serviceInstance + "Logs");
// // alert( $form.clone().wrap('<p>').parent().html() );
// // alert("form is :" + $form.html() ) ;
// $('body').append($form);
//	
// // hook for chrome not liking mult-wins opened
// if ( $.browser.chrome ) {
// //alert ("chrome") ;
// setTimeout( function() {
// $form.submit();
// $form.remove() ;
//			
// }, winIndex*500);
// } else {
// $form.submit();
// $form.remove() ;
// }
// }
function removeFromArray(arrayObject, valToRemove) {
	 return jQuery.grep( arrayObject, function (value) {
		  return value != valToRemove;
	 } );
}

// http://goessner.net/articles/JsonPath/
// https://github.com/wilhelm-murdoch/jQuery-JSONPath
(function ($) {
	 $.JSONPath = function (options) {
		  var options = $.extend( {
				data: {},
				resultType: 'VALUE',
				keepHistory: true,
				onFound: function (path, value) {
				},
				onNormalize: function (expression) {
				},
				onTrace: function (expression, value, path) {
				},
				onWalk: function (loc, expression, value, path, func) {
				}
		  }, options );

		  /*
			* JSONPath 0.8.5 - XPath for JSON
			* 
			* Copyright (c) 2007 Stefan Goessner (goessner.net) Licensed under the
			* MIT (MIT-LICENSE.txt) licence.
			*/
		  var P = {
				result: [],
				normalize: function (expr) {
					 options.onNormalize( expr )

					 var subx = [];
					 return expr.replace( /[\['](\??\(.*?\))[\]']|\['(.*?)'\]/g,
								function ($0, $1, $2) {
									 return '[#' + (subx.push( $1 || $2 ) - 1) + ']';
								} ) /* http://code.google.com/p/jsonpath/issues/detail?id=4 */
								.replace( /'?\.'?|\['?/g, ';' ).replace( /;;;|;;/g, ';..;' )
								.replace( /;$|'?\]|'$/g, '' ).replace( /#([0-9]+)/g,
								function ($0, $1) {
									 return subx[$1];
								} );
				},
				asPath: function (path) {
					 var x = path.split( ';' ), p = '$';
					 for ( var i = 1, n = x.length; i < n; i++ )
						  p += /^[0-9*]+$/.test( x[i] ) ? ("[" + x[i] + "]") : ("['"
									 + x[i] + "']");
					 return p;
				},
				store: function (p, v) {
					 options.onFound( p, v )

					 if ( p ) {
						  switch (options.resultType) {
								case 'PATH':
									 P.result[P.result.length] = P.asPath( p )
									 break
								case 'BOTH':
									 P.result[P.result.length] = {
										  path: P.asPath( p ),
										  value: v,
										  query: p
									 }
									 break;
								default:
								case 'VALUE':
									 P.result[P.result.length] = v
									 break
						  }
					 }
					 return !!p;
				},
				trace: function (expr, val, path) {
					 options.onTrace( expr, val, path )

					 if ( expr !== '' ) {
						  var x = expr.split( ';' ), loc = x.shift();
						  x = x.join( ';' );
						  if ( val && val.hasOwnProperty( loc ) )
								P.trace( x, val[loc], path + ';' + loc );
						  else if ( loc === '*' )
								P.walk( loc, x, val, path, function (m, l, x, v, p) {
									 P.trace( m + ';' + x, v, p );
								} );
						  else if ( loc === '..' ) {
								P.trace( x, val, path );
								P.walk( loc, x, val, path, function (m, l, x, v, p) {
									 typeof v[m] === 'object'
												&& P.trace( '..;' + x, v[m], p + ';' + m );
								} );
						  } else if ( /^\(.*?\)$/.test( loc ) ) // [(expr)]
								P.trace( P.eval( loc, val, path.substr( path
										  .lastIndexOf( ';' ) + 1 ) )
										  + ';' + x, val, path );
						  else if ( /^\?\(.*?\)$/.test( loc ) ) // [?(expr)]
								P.walk( loc, x, val, path, function (m, l, x, v, p) {
									 if ( P.eval( l.replace( /^\?\((.*?)\)$/, '$1' ),
												v instanceof Array ? v[m] : v, m ) )
										  P.trace( m + ';' + x, v, p );
								} ); // issue 5 resolved
						  else if ( /^(-?[0-9]*):(-?[0-9]*):?([0-9]*)$/.test( loc ) ) // [start:end:step]
								// phyton
								// slice
								// syntax
								P.slice( loc, x, val, path );
						  else if ( /,/.test( loc ) ) { // [name1,name2,...]
								for ( var s = loc.split( /'?,'?/ ), i = 0, n = s.length; i < n; i++ )
									 P.trace( s[i] + ';' + x, val, path );
						  }
					 } else
						  P.store( path, val );
				},
				walk: function (loc, expr, val, path, f) {
					 if ( val instanceof Array ) {
						  for ( var i = 0, n = val.length; i < n; i++ )
								if ( i in val )
									 f( i, loc, expr, val, path );
					 } else if ( typeof val === 'object' ) {
						  for ( var m in val )
								if ( val.hasOwnProperty( m ) )
									 f( m, loc, expr, val, path );
					 }
				},
				slice: function (loc, expr, val, path) {
					 if ( val instanceof Array ) {
						  var len = val.length, start = 0, end = len, step = 1;
						  loc.replace( /^(-?[0-9]*):(-?[0-9]*):?(-?[0-9]*)$/g,
									 function ($0, $1, $2, $3) {
										  start = parseInt( $1 || start );
										  end = parseInt( $2 || end );
										  step = parseInt( $3 || step );
									 } );
						  start = (start < 0) ? Math.max( 0, start + len ) : Math.min(
									 len, start );
						  end = (end < 0) ? Math.max( 0, end + len ) : Math.min( len,
									 end );
						  for ( var i = start; i < end; i += step )
								P.trace( i + ';' + expr, val, path );
					 }
				},
				eval: function (x, _v, _vname) {
					 try {
						  return $
									 && _v
									 && eval( x.replace( /(^|[^\\])@/g, '$1_v' ).replace(
												/\\@/g, '@' ) );
					 } // issue 7 : resolved ..
					 catch (e) {
						  throw new SyntaxError( 'jsonPath: '
									 + e.message
									 + ': '
									 + x.replace( /(^|[^\\])@/g, '$1_v' ).replace( /\\@/g,
									 '@' ) );
					 } // issue 7 : resolved ..
				}
		  };

		  this.options = function (updated_options) {
				options = $.extend( options, updated_options );
				return this;
		  };

		  // bug fix !! find seems to collide with jquery native
		  this.xfind = function (expression) {
				if ( !options.keepHistory ) {
					 P.result = [];
				}

				if ( expression ) {
					 P.trace( P.normalize( expression ).replace( /^\$;?/, '' ),
								options.data, '$' ); // issue 6 resolved
					 return P.result.length ? P.result : false;
				}
		  };

		  return this;
	 };
})( jQuery );

//
// Jquery EOL'ed the $.browser.version api....adding for backwards compatibility
// with several plugins.
(function () {

	 var matched, browser;

	 // Use of jQuery.browser is frowned upon.
	 // More details: http://api.jquery.com/jQuery.browser
	 // jQuery.uaMatch maintained for back-compat
	 jQuery.uaMatch = function (ua) {
		  ua = ua.toLowerCase();

		  var match = /(chrome)[ \/]([\w.]+)/.exec( ua )
					 || /(webkit)[ \/]([\w.]+)/.exec( ua )
					 || /(opera)(?:.*version|)[ \/]([\w.]+)/.exec( ua )
					 || /(msie) ([\w.]+)/.exec( ua ) || ua.indexOf( "compatible" ) < 0
					 && /(mozilla)(?:.*? rv:([\w.]+)|)/.exec( ua ) || [];

		  return {
				browser: match[1] || "",
				version: match[2] || "0"
		  };
	 };

	 matched = jQuery.uaMatch( navigator.userAgent );
	 browser = {};

	 if ( matched.browser ) {
		  browser[matched.browser] = true;
		  browser.version = matched.version;
	 }

	 // Chrome is Webkit, but Webkit is also Safari.
	 if ( browser.chrome ) {
		  browser.webkit = true;
	 } else if ( browser.webkit ) {
		  browser.safari = true;
	 }

	 jQuery.browser = browser;

	 jQuery.sub = function () {
		  function jQuerySub(selector, context) {
				return new jQuerySub.fn.init( selector, context );
		  }
		  jQuery.extend( true, jQuerySub, this );
		  jQuerySub.superclass = this;
		  jQuerySub.fn = jQuerySub.prototype = this();
		  jQuerySub.fn.constructor = jQuerySub;
		  jQuerySub.sub = this.sub;
		  jQuerySub.fn.init = function init(selector, context) {
				if ( context && context instanceof jQuery
						  && !(context instanceof jQuerySub) ) {
					 context = jQuerySub( context );
				}

				return jQuery.fn.init.call( this, selector, context, rootjQuerySub );
		  };
		  jQuerySub.fn.init.prototype = jQuerySub.fn;
		  var rootjQuerySub = jQuerySub( document );
		  return jQuerySub;
	 };

})();


function adjustOffset(el, offset) {
	 /* From http://stackoverflow.com/a/8928945/611741 */
	 var val = el.value, newOffset = offset;
	 if ( val.indexOf( "\r\n" ) > -1 ) {
		  var matches = val.replace( /\r\n/g, "\n" ).slice( 0, offset ).match( /\n/g );
		  newOffset += matches ? matches.length : 0;
	 }
	 return newOffset;
}


// 
(function ($) {
	 // Behind the scenes method deals with browser
	 // idiosyncrasies and such
	 $.caretTo = function (el, index) {
		  if ( el.createTextRange ) {
				var range = el.createTextRange();
				range.move( "character", index );
				range.select();
		  } else if ( el.selectionStart != null ) {
				el.focus();
				el.setSelectionRange( index, index );
		  }
	 };

	 // https://gist.github.com/DrPheltRight/1007907
	 // The following methods are queued under fx for more
	 // flexibility when combining with $.fn.delay() and
	 // jQuery effects.

	 // Set caret to a particular index
	 $.fn.caretTo = function (index, offset) {
		  return this.queue( function (next) {
				if ( isNaN( index ) ) {
					 var i = $( this ).val().indexOf( index );
					 if ( offset === true ) {
						  i += index.length;
					 } else if ( offset ) {
						  i += offset;
					 }
					 $.caretTo( this, i );
				} else {
					 $.caretTo( this, index );
				}
				next();
		  } );
	 };

	 // Set caret to beginning of an element
	 $.fn.caretToStart = function () {
		  return this.caretTo( 0 );
	 };

	 // Set caret to the end of an element
	 $.fn.caretToEnd = function () {
		  return this.queue( function (next) {
				$.caretTo( this, $( this ).val().length );
				next();
		  } );
	 };


	 $.fn.setCursorPosition = function (position) {
		  /* From http://stackoverflow.com/a/7180862/611741 */
		  if ( this.lengh == 0 )
				return this;
		  return $( this ).setSelection( position, position );
	 }

	 $.fn.setSelection = function (selectionStart, selectionEnd) {
		  /* From http://stackoverflow.com/a/7180862/611741 
			modified to fit http://stackoverflow.com/a/8928945/611741 */
		  if ( this.length == 0 )
				return this;
		  input = this[0];


		  input.focus();
		  selectionStart = adjustOffset( input, selectionStart );
		  selectionEnd = adjustOffset( input, selectionEnd );
		  input.setSelectionRange( selectionStart, selectionEnd );


		  return this;
	 }

	 $.fn.focusEnd = function () {
		  /* From http://stackoverflow.com/a/7180862/611741 */
		  this.setCursorPosition( this.val().length );
	 }
	 // http://stackoverflow.com/questions/1891444/how-can-i-get-cursor-position-in-a-textarea
	 $.fn.getCursorPosition = function () {
		  var el = $( this ).get( 0 );
		  var pos = 0;
		  if ( 'selectionStart' in el ) {
				pos = el.selectionStart;
		  } else if ( 'selection' in document ) {
				el.focus();
				var Sel = document.selection.createRange();
				var SelLength = document.selection.createRange().text.length;
				Sel.moveStart( 'character', -el.value.length );
				pos = Sel.text.length - SelLength;
		  }
		  return pos;
	 }

}( jQuery ));

// http://stackoverflow.com/questions/273789/is-there-a-version-of-javascripts-string-indexof-that-allows-for-regular-expr

String.prototype.regexIndexOf = function (regex, startpos) {
	 var indexOf = this.substring( startpos || 0 ).search( regex );
	 return (indexOf >= 0) ? (indexOf + (startpos || 0)) : indexOf;
};


String.prototype.contains = function (searchString) {
	 return this.indexOf( searchString ) !== -1;
};
