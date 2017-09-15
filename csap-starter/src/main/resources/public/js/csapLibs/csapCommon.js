function CsapCommon() {
} 

CsapCommon.labelTableRows = function( $table ) {
	$( "tr td:first-child", $table).each(function( index ) {
		var $label = jQuery('<div/>', {
			class: 'tableLabel',
			text: (index+1) + "."
				
		});
		  $(this).prepend( $label ) ;
		  $label.css("height", $(this).css("height")) ;
	});
}

CsapCommon.configureCsapToolsMenu = function() {

	var $toolsMenu = $("header div.csapOptions select");
	$toolsMenu.selectmenu({
		width : "20em",
		change : function() {
			var item = $("header div.csapOptions select").val();

			if (item != "default") {
				console.log("launching: " + item);
				CsapCommon.openWindowSafely(item, "_blank");
				$("header div.csapOptions select").val("default")
			}

			$toolsMenu.val("default");
			$toolsMenu.selectmenu("refresh");
		}

	});
}

CsapCommon.configureCsapAlertify = function() {
	// http://alertifyjs.com/
	alertify.defaults.glossary.title = "CSAP"
	alertify.defaults.theme.ok = "ui positive mini button";
	alertify.defaults.theme.cancel = "ui black mini button";
	alertify.defaults.notifier.position = "top-left";
	alertify.defaults.closableByDimmer = false;
	// alertify.defaults.theme.ok = "pushButton";
	// alertify.defaults.theme.cancel = "btn btn-danger";

	alertify.csapWarning = function(message) {
		var $warning = jQuery('<div/>', {});
		$warning.append(jQuery('<div/>', {
			class : "news",
			html : message
		}).css("font-size", "1em").css("width", "90%").css("max-height",
				"600px").css("overflow", "auto"));

		$warning
				.append('<br/><button class="pushButton">Click to Dismiss</button><br/>');

		return alertify.error($warning.html(), 0);
	}

}

CsapCommon.openWindowSafely = function(url, windowFrameName) {

	// console.log("window frame name: " + getValidWinName( windowFrameName)
	// + "
	// url: " + encodeURI(url)
	// + " encodeURIComponent:" + encodeURIComponent(url)) ;

	window.open(encodeURI(url), CsapCommon.getValidWinName(windowFrameName));

}

CsapCommon.getValidWinName = function(inputName) {
	var regex = new RegExp("-", "g");
	var validWindowName = inputName.replace(regex, "");

	regex = new RegExp(" ", "g");
	validWindowName = validWindowName.replace(regex, "");

	return validWindowName;
}
