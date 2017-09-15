
// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config({
// needed by graphs
// paths: {
// mathjs: "../../csapLibs/mathjs/modules/math.min"
// },
// packages: [
// { name: 'graphPackage',
// location: '../../graphs/modules/graphPackage', // default 'packagename'
// main: 'ResourceGraph' // default 'main'
// }
// ]
});

require([], function() {
	console.log("\n\n ************** module loaded *** \n\n");

	var $metricDetails = $("#metricDetails") ;
	var $loading = $(".loadingBody") ;
	var $alertsBody = $("#alertsBody") ;
	var $defBody = $("#defBody") ;
	var $healthTable = $("#health") ;
	var $numberOfHours = $("#numberHoursSelect") ;
	var $metricTable = $("#metricTable") ;
	var $metricBody = $("#metricBody") ;
	var _alertsCountMap = new Object() ;

	var SECOND_MS=1000;
	var MINUTE_MS=60*SECOND_MS;
	var HOUR_MS=60*MINUTE_MS;
	var _refreshTimer = null ;

	$(document).ready(function() {
		CsapCommon.configureCsapAlertify();
		initialize();

	});

	function initialize() {
		//$( "#tabs" ).tabs() ;
		$( "#tabs" ).tabs( {
			activate: function ( event, ui ) {

				console.log( "Loading: " + ui.newTab.text() );
				if ( ui.newTab.text().indexOf( "Metrics" ) != -1 ) {
					getMetrics() ;
				}

			}
		} );

		$numberOfHours.change( getAlerts );

		$("#refreshAlerts").click( function() { getAlerts() } );
		

		$(".alertEnabled").change( function() { 
			
			var enabled="false" ;
			if ( $( this ).is( ":checked" ) ) enabled="true" ;
			var params = { "id": $(this).data("id"), "enabled": enabled } ;
			$.getJSON(
					baseUrl + "/../toggleMeter", params )
					.done(
							function ( metricResponse ) {
								
								alertify.alert( "Response: " +  JSON.stringify( metricResponse, null, "\t" )) ;
								
							} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {
						console.log( "Failed command", jqXHR) ;
						handleConnectionError( "toggling alerts, Response: " + jqXHR.responseText , errorThrown );
					} );
		} );
		
		
		// alerts will refresh getMetrics
		$("#refreshMetrics").click( function() { getAlerts() } );
		$("#lastStartMetrics").click( function() { getMetrics("restart") } );
		
		$("#clearMetrics").click(function() {
			$.getJSON(
					baseUrl + "/../clearMetrics" )
					.done(
							function ( metricResponse ) {
								
								alertify.alert( "Cleared: " + metricResponse.numStops + " Stopwatches and " + metricResponse.numCounters + " Counters") ;
								getMetrics() ;
								
							} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {

						handleConnectionError( "clearing alerts", errorThrown );
					} );
		});
		
		
		$( "#metricFilter" ).keyup( function () {
			// 
			clearTimeout(_refreshTimer) ;
			_refreshTimer = setTimeout( function() {
				filterMetrics();
			}, 500) ;

			
			return false;
		} );
		

		$.tablesorter.addParser({
			// set a unique id
			id : 'raw',
			is : function(s, table, cell, $cell) {
				// return false so this parser is not auto detected
				return false;
			},
			format : function(s, table, cell, cellIndex) {
				var $cell = $(cell);
				// console.log("timestamp parser", $cell.data('timestamp'));
				// format your data for normalization
				return $cell.data('raw');
			},
			// set type, either numeric or text
			type : 'numeric'
		});

		$healthTable.tablesorter({
			sortList : [ [ 0, 1 ] ],
			theme : 'csap'
		});
		
		getAlerts() ;
		$metricTable.tablesorter({
			sortList : [ [ 1, 1 ] ],
			theme : 'csap'
		});
		
		$("tr", $defBody).each( function ( index ) {
			var $defRow = $(this) ;
			var defId = $( ":nth-child(1) span", $defRow).text().trim() ;
			_alertsCountMap[defId] = 0 ;
		});
		

	}
	
	function getMetrics( sampleName ) {
		
		$loading.show() ;
		
		var params = {} ;
		
		if ( sampleName ) {
			$.extend( params, {
				"sampleName": sampleName
			} );
		}
		$.getJSON(
				baseUrl + "/../metrics", params )
				.done(
						function ( metricResponse ) {

							$loading.hide() ;
							//console.log("alertResponse", alertResponse) ;
							
							$("#healthStatus").empty() ;
							var $alertImage = jQuery( '<img/>', { 
								src: imagesBase + "/16x16/green.png",
								 class: "loadMetric"
							} ) ;
							if ( ! metricResponse.healthReport.isHealthy ) {
								$alertImage = jQuery( '<img/>', { 
									src: imagesBase + "/16x16/red.png",
									class: "loadMetric"
								} ) ;
							}
							
							$("#healthStatus").parent().attr("title", "last refreshed: " + metricResponse.healthReport.lastCollected);
							
							$("#healthStatus").append($alertImage) ;

							$metricBody.empty() ;
							var metrics = metricResponse.rows ;
							if ( metrics.length == 0 ) {
								var $row = jQuery( '<tr/>', { } );
								
								$row.appendTo( $metricBody ) ;
								
								$row.append( jQuery( '<td/>', {
									colspan: 99,
									text: "No metrics found."
								} ) )
							} else {
								addMetrics( metrics ) ;
							}
							
							$metricTable.trigger("update") ;
							
							setTimeout( filterMetrics, 500) ;

						} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "getting alerts", errorThrown );
				} );
		
	}
	
	function getMetricItem( name) {
		var params = { "name": name } ;
		$.getJSON(
				baseUrl + "/../metric", params )
				.done(
						showMetricDetails )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "clearing alerts", errorThrown );
				} );
	}
	
	
	function showMetricDetails( metricResponse ) {

		$(".name", $metricDetails).text( metricResponse.name ) ;
		$("#firstTime", $metricDetails).text( metricResponse.firstUsage ) ;
		$("#lastTime", $metricDetails).text( metricResponse.lastUsage ) ;
		$("#maxTime", $metricDetails).text( metricResponse.maxTimeStamp ) ;
		
		var detailItems = metricResponse.details.split(",") ;
		
		var $tbody = $( "tbody" , $metricDetails) ;
		$tbody.empty() ;
		
		for ( var i=0; i< detailItems.length ; i++ ) {

			var $row = jQuery( '<tr/>', { } );
			$row.appendTo( $tbody ) ;
			
			var items = detailItems[i].split("=") ;
			
			if ( items[0].contains("name") ||  items[0].contains("note") ) continue;

			jQuery( '<td/>', {
				text: items[0]
			} ).appendTo ( $row ) ;
			jQuery( '<td/>', {
				text: items[1]
			} ).appendTo ( $row ) ;
		}
		
		
		
		
		alertify.alert( $metricDetails.html() ) ;
		
	} 
	
	
	
	function numberWithCommas(x) {
	    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
	}
	
	
	
	
	function addMetrics( metrics ) {
		$loading.hide() ;
		
		for (var i=0; i < metrics.length ; i++ ) {
			var $row = jQuery( '<tr/>', { } );
			
			var metric= metrics[i]
			
			$row.appendTo( $metricBody ) ;
			
			var $nameLink = jQuery( '<a/>', { 
				href: "#loadMetric",
				title: "Click to view metric details",
				 class: "simple",
				 "data-name": metric.name,
				 text: metric.name
			} ).click( function() { 
				getMetricItem($(this).data("name") ) ;
				return false;
			} )
			
			var $nameCell = jQuery( '<td/>', {} ) ;
			$nameCell.append( $nameLink ) ;
			$nameCell.appendTo ( $row ) ;
			
			
			var alertContents = "-" ;
			var $alertImage = "" ;
			var alertValue=-1;
			if ( _alertsCountMap[metric.name.trim() ] == 0) {

					console.log("defId", metric.name) ;
					alertContents = "" ;
					$alertImage = jQuery( '<img/>', { 
						src: imagesBase + "/16x16/green.png",
						 class: "loadMetric"
					} ) ;
					alertValue = 0;
			} else if (_alertsCountMap[metric.name.trim() ] > 0 ) {

				console.log("defId", metric.name) ;
				alertContents = _alertsCountMap[metric.name.trim()] ;
				$alertImage = jQuery( '<img/>', { 
					src: imagesBase + "/16x16/red.png",
					 class: "loadMetric"
				} ) ;
				alertValue = 1;
			}
				
			
			var $alertCell = jQuery( '<td/>', {
				text: alertContents ,
				"data-raw": alertValue 
			} ); 
			
			if ( $alertImage != "" ) {
				$alertCell.append( $alertImage ) ;
			}
			$alertCell.appendTo ( $row ) ; 
			
			
			
		
			var collectedVals = metric.data ;
			
			var testCounter = collectedVals[0] ;
			for (var j=0; j < collectedVals.length ; j++ ) {
				var collected = collectedVals[j] ;
				var showVal = collected;
				
				if ( j > 0 && testCounter == 0 ) {
					showVal = "-" ;
					collected = -1;
				} else if ( j > 0 ) {
					showVal = collected + "<span>ms</span>";

					if ( collected > 24*HOUR_MS ) {
						showVal = (collected/24/HOUR_MS).toFixed(1) + "<span>days</span>";
					} else if ( collected > HOUR_MS ) {
						showVal = (collected/HOUR_MS).toFixed(1) + "<span>hrs</span>";
					} else if ( collected > MINUTE_MS ) {
						showVal = (collected/MINUTE_MS).toFixed(1) + "<span>min</span>";
					} else if ( collected > (SECOND_MS) ) {
						showVal = (collected/SECOND_MS).toFixed(2) + "<span>s</span>";
					}
				} else {
					showVal = numberWithCommas(collected) ;
				}

				jQuery( '<td/>', {
					html: showVal,
					"data-raw": collected
				} ).appendTo ( $row ) ;
			}
			// add spacers
			for (var j=collectedVals.length; j < 5  ; j++ ) {

				jQuery( '<td/>', {
					text: "-",
					"data-raw": -1
				} ).appendTo ( $row ) ;
			}
			

		}
		
	}
	
	function filterMetrics() {
		var simonFilter = $( "#metricFilter" ).val().trim().toLowerCase();
		
//		if ( simonFilter.length == 0) {
//			console.log("No filters") ;
//			return;
//		}
		console.log("applying filter: ", simonFilter) ;
		$( "tr td:first-child", $metricBody).each( function ( index ) {
			var simonName = $(this).text().toLowerCase() ;
			var $row = $(this).parent() ;
			if ( simonFilter.length > 0 && simonName.indexOf(simonFilter) == -1 ) {
				$row.hide() ;
			} else {
				$row.show() ;
			}
		}) ;

	}
	
	function getAlerts() {
		
		$loading.show() ;
		
		var paramObject = {
			hours: $numberOfHours.val()
		} ;
		
		if (testCountParam ) {
			$.extend( paramObject, {
				testCount: testCountParam
			} );
		}
		
		$.getJSON(
				baseUrl + "/../report", paramObject )
				.done(
						function ( alertResponse ) {

							$loading.hide() ;
							console.log("alertResponse", alertResponse) ;
							

							$alertsBody.empty() ;
							var alerts = alertResponse.triggered ;
							if ( alerts.length == 0 ) {
								var $row = jQuery( '<tr/>', { } );
								
								$row.appendTo( $alertsBody ) ;
								
								$row.append( jQuery( '<td/>', {
									colspan: 99,
									text: "No alerts found. Adjust filters as needed."
								} ) )
							} else {
								addAlerts( alerts ) ;
							}
							
							$healthTable.trigger("update") ;
							getMetrics() ;

						} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "getting alerts", errorThrown );
				} );
		
	}
	
	function addAlerts( alerts ) {
		
		for ( var id in _alertsCountMap )  {
			_alertsCountMap[id] = 0;
		}
		for (var i=0; i < alerts.length ; i++ ) {
			var $row = jQuery( '<tr/>', { } );
			
			var alert= alerts[i]
			
			$row.appendTo( $alertsBody ) ;
			
			jQuery( '<td/>', {
				text: alert.time,
				"data-raw": alert.ts
			} ).appendTo ( $row ) ;
			
			jQuery( '<td/>', {
				text: alert.id
			} ).appendTo ( $row ) ;
			
			_alertsCountMap[alert.id] = _alertsCountMap[alert.id] + 1 ;
			
			jQuery( '<td/>', {
				text: alert.type
			} ).appendTo ( $row ) ;
			
			var desc = alert.description ;
			if ( alert.count > 1 ) {
				desc = desc + "<br/><div>Alerts Throttled: <span>" + alert.count + "</span></div>"  ;
			}
			jQuery( '<td/>', {
				html: desc
			} ).appendTo ( $row ) ;
		}
		
		if ( alerts.length > 0 ) {
			_alertsCountMap["csap.health.report.fail"] = alerts.length ;
		} else {
			_alertsCountMap["csap.health.report.fail"] = 0 ;
		}
	}

});