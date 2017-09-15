
var serviceUrl = "/admin/api/application/";
function CsapUser() {

	// Hooks for testing off of the lb urls
	if ( document.URL.indexOf( "cisco.com/" ) == -1 )
		serviceUrl = "https://csap-secure.cisco.com/admin/api/application/";
	if ( document.URL.indexOf( "CsAgent:8011" ) != -1 )
		serviceUrl = "/CsAgent/api/application/";

	console.log( "serviceUrl: " + serviceUrl + " current URl" + document.URL );


	var that = this;   // this is temp

	this.getUserNames = function ( useridArray, callBackFunction ) {

		var requestParms = {
			"userid": useridArray
		};

		$.getJSON(
				serviceUrl + "userNames?callback=?",
				requestParms )

				.done( function ( userJson ) {
					getUserNamesSuccess( userJson, callBackFunction );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Service names", errorThrown );
				} );
	};


	this.getUserNamesSuccess = getUserNamesSuccess;
	function getUserNamesSuccess( userJson, callBackFunction ) {
		//console.debug("CsapUser.getUserNamesSuccess: " + JSON.stringify(userJson ));

		// Invoke callback
		callBackFunction( userJson );

	}



	this.getUserInfo = function ( userid, callBackFunction ) {

		var requestParms = { };

		$.getJSON(
				serviceUrl + "userInfo/" + userid + "?callback=?",
				requestParms )

				.done( function ( userJson ) {
					getUserInfoSuccess( userJson, callBackFunction );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
				} );
	};


	this.getUserInfoSuccess = getUserInfoSuccess;
	function getUserInfoSuccess( userJson, callBackFunction ) {
		console.debug( "CsapUser.getUserInfoSuccess: " );

		// Invoke callback

		if ( callBackFunction != null ) {
			console.log( "Invoking callback" );
			callBackFunction( userJson );
			return;
		}

		if ( typeof alertify != 'undefined' ) {

			// construct element
			var containerJQ = jQuery( '<div/>', { } );
			getUserContainer( userJson, true ).appendTo( containerJQ );

			alertify.alert( containerJQ.html() );
			$( ".alertify-inner" ).css( "text-align", "left" );
			$( ".alertify" ).css( "width", "600px" );
			$( ".alertify" ).css( "margin-left", "-300px" );
		} else
			alert( JSON.stringify( userJson ) );

	}


	function getUserContainer( userJson, isIncludeLinks ) {
		// construct element

		var section = jQuery( '<section/>', { id: "csapUser" } );
		jQuery( '<header/>', { style: "margin-bottom: 0.5em" } ).html( userJson.fullName + ' <span style="display:inline-block; padding-left: 3em">' + userJson.title + '</span>' ).appendTo( section );
		jQuery( '<span/>', { class: "cLabel" } ).text( "userid: " ).appendTo( section );
		jQuery( '<span/>', { class: "value" } ).text( userJson.userid ).appendTo( section );
		jQuery( '<span/>', { class: "cLabel" } ).text( "Email: " ).appendTo( section );
		jQuery( '<span/>', { class: "value" } ).text( userJson.mail ).appendTo( section );
		section.append( "<br>" );
		jQuery( '<span/>', { class: "cLabel" } ).text( "Phone: " ).appendTo( section );
		jQuery( '<span/>', { class: "value" } ).text( userJson.telephoneNumber ).appendTo( section );
		jQuery( '<span/>', { class: "cLabel" } ).text( "Type: " ).appendTo( section );
		jQuery( '<span/>', { class: "value" } ).text( userJson.employeeType ).appendTo( section );

		if ( isIncludeLinks ) {
			section.append( "<br>" );
			jQuery( '<span/>', { class: "cLabel" } ).text( "Link: " ).appendTo( section );
			jQuery( '<span/>', { class: "value" } ).append(
					jQuery( '<a/>', {
						class: 'simple',
						href: 'http://wwwin-tools.cisco.com/dir/details/' + userJson.userid,
						text: userJson.userid,
						target: '_blank',
					} ).css( "display", "inline" ) ).appendTo( section );
			jQuery( '<span/>', { class: "cLabel" } ).text( "Manager: " ).appendTo( section );
			jQuery( '<a/>', {
				class: 'simple',
				href: 'http://wwwin-tools.cisco.com/dir/details/' + userJson.manager,
				text: userJson.manager,
				target: '_blank',
			} ).css( "display", "inline" ).appendTo( section );


		} else {

			section.append( "<br>" );
			jQuery( '<div/>', { style: "font-style: italic;font-weight: bold;margin-top:0.5em" } ).text( "Click on userid to browse Cisco Directory" ).appendTo( section );
		}

		section.append( "<br>" );
		jQuery( '<div/>', { class: "note" } ).text( userJson.location ).appendTo( section );

		// jQuery('<div/>', {	class:"note"}).text( JSON.stringify(userJson ) ).appendTo(containerJQ) ;

		return section;
	}

	var hoverTimer = 0;
	this.onHover = onHover;
	function onHover( selectorJQ, delay ) {

		// unregister previous events
		selectorJQ.off();

		//

		selectorJQ.each( function ( index, value ) {
			// alert( index + ": " + value );
			// $(this).text("peter") ;

			var $userInfo = $( this );
			var userid = $userInfo.text();

			if ( userid != "System" && userid != "CsAgent" ) {
				if ( $( "a", $userInfo ).length == 0 ) {
					// add link
					$userInfo.html( jQuery( '<a/>', {
						class: 'simple',
						href: 'http://wwwin-tools.cisco.com/dir/details/' + userid,
						text: userid,
						target: '_blank',
					} ).css( "display", "inline-block" ).css( "padding-right", "1em" ) );
				}
				$userInfo.hover(
						function () {
							hoverTimer = setTimeout( function () {
								$_lastUserInfo = $userInfo;
								that.getUserInfo( userid, showPopUp );
							}, delay );
						},
						function () {
							clearTimeout( hoverTimer );
							$( ".csapUserPopup" ).remove();
							//useridCellJQ.text( useridCellJQ.text().toLowerCase() )  ;
						} );
			}
		} );
	}

	var $_lastUserInfo = null;
	function showPopUp( userJson ) {
		//console.info( "CsapUser.onHover.showPopUp: ", $_lastUserInfo.offset().top, userJson );
		//console.log(lastCell_JQ.text() ) ;
		var $userInfoPanel = jQuery( '<div/>', {
			style: "position: absolute; width: 30em; top: 10px",
			class: "csapUserPopup"
		} );
		getUserContainer( userJson, false ).appendTo( $userInfoPanel );
		$userInfoPanel.hide();
		$( "body" ).append( $userInfoPanel );
		//$userInfoPanel.position( { my: "left bottom", at: "right top", of: $_lastUserInfo } );
		var panelTop = Math.round( ($_lastUserInfo.offset().top ) -200 ) ;
		var panelLeft = Math.round($_lastUserInfo.offset().left + 100 ) ;
		//console.log("panelTop: " + panelTop + " panelLeft: " + panelLeft)
		$userInfoPanel.offset(
				{
					top: panelTop,
					left: panelLeft
				} );
		//$userInfoPanel.offset( { top: 10 , left: 500}  );
		$userInfoPanel.show();

	}
	;


	this.handleConnectionError = handleConnectionError;
	function handleConnectionError( command, errorThrown ) {
		var message = "Failed command: " + command;
		message += "\n\n Server Message:" + errorThrown;

		console.log( message );
	}


}
