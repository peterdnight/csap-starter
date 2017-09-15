
$(document).ready(function() {

	var demo = new DemoManager();
	demo.appInit();

});

function DemoManager() {

	// note the public method
	this.appInit = function() {
		console.log("Init csap alertify");
		CsapCommon.configureCsapAlertify();
		CsapCommon.labelTableRows( $("table") ) ;

		
		$('#dbConnectionTest').click(function() {

			var message = "Testing Db Connection " ;
			try {
				alertify.notify(message) ;
				testDbConnection() ;
			} catch (e) {
				console.log (e)
			}
			return false; // prevents link
		});
		
		$('.showData').click(function() {

			var message = "Getting items from DB " ;
			alertify.notify(message) ;
			
			 getData() ;
			return false; // prevents link
		});
		

		$('.longTime').click(function() {
			alertify.alert("Warning: this request might take a while. Once completed - the results will be display") ;
			return true; // prevents link
		});
		
		if ( $("#inlineResults").text() != "" ) {
			alertify.csapWarning ( '<pre style="font-size: 0.8em">'+$("#inlineResults").text()+"</pre>" ) ;
		}
	};

	function testDbConnection(  ) { 

		$('body').css('cursor', 'wait');
		   $.post( $('#dbConnectionForm').attr("action"), $('#dbConnectionForm').serialize(), function(data) {
		         // alertify.alert(data) ;
			   alertify.dismissAll();
			   alertify.csapWarning ( '<pre style="font-size: 0.8em">'+data+"</pre>" )
		 		$('body').css('cursor', 'default');
		       },
		       'text' // I expect a JSON response
		    );
	}

	function getData(  ) { 

		$('body').css('cursor', 'wait');
		$.getJSON(  
				"../api/showTestDataJson",  
				{	
					dummyParam: "dummy" 
				} )

				.success ( function( loadJson ) { getDataSuccess( loadJson); } )

				.error(function(jqXHR, textStatus, errorThrown) {

					handleConnectionError( "Getting Items in DB"  , errorThrown ) ;
				});
	}
 

	function getDataSuccess( dataJson ) {

		alertify.notify("Number of items in DB:" + dataJson.count) ;
		$(".alertify-logs").css("width", "800px") ;
		
	    var table = $("#ajaxResults table").clone() ;

	
	    for (var i = 0; i <  dataJson.data.length; i++) {
	    	
	    	var trContent='<td style="padding: 2px;text-align: left">'+dataJson.data[i].id+'</td><td style="padding: 2px;text-align: left">'+dataJson.data[i].description+'</td>' ;
		    var tr = $('<tr />', {'class': "peter", html: trContent});
		    table.append(tr);
		}
	    
	    var message="Number of records displayed: " + dataJson.data.length + ", of total in db: " +  dataJson.count + "<br><br>";
	    if (  dataJson.count == 0) {
	    	var trContent='<td style="padding: 2px;text-align: left">-</td><td style="padding: 2px;text-align: left">No Data Found</td>' ;
		    var tr = $('<tr />', {'class': "peter", html: trContent});
		    table.append(tr);
	    }
	    alertify.dismissAll();
		alertify.alert( message + table.clone().wrap('<p>').parent().html() ).set('modal',false) ;

		$('body').css('cursor', 'default');
	}
	


	function handleConnectionError( command , errorThrown ) {
		var message= "<pre>Failed connecting to server";
		message += "\n\n Server Message:" + errorThrown ;
		message += "\n\n Click OK to reload page, or cancel to ignore.</pre>" ;
		
		alertify.csapWarning(message);
		$('body').css('cursor', 'default');
	}     
	
}
