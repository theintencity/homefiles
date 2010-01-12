<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:template match="/">

<html>
  <head>
    <title>File Sync: Distributed Software Development Project</title> 
    <link rel="stylesheet" type="text/css" href="/all/html/file/style.css"/>
	<script type="text/javascript">
		<![CDATA[
		var root = "";

		function settings() {
			root = prompt("please specify a new root directory", root);
			if (root == null) {
				root = "";
			}
			if (root!="") {
				window.location = '/settings?rootdir=' + escape(root).replace(/\//g, '%2F');
			}
		}

		function search(value) {
			window.location = '?contains=' + value;
		}
		
		function searchModified() {
			date = prompt("please secify a date in MM-dd-yyyy format, e.g., 12-31-2009 for Dec 31, 2009");
			window.location = '?modifiedsince=' + date;
			//alert("search is currently not implemented");
		}
		
		function fileview() {
			window.location = '/all/html/filelist';
			//var url = new String(window.location);
			//window.location = url.replace(/\?.*$/, "");
   		}
   		
   		function gotoDevice(deviceName) {
   			window.location = '/' + deviceName + '/html/filelist';
   		}
   		
   		function logout() {
   			window.location = '/logout';
   		}
   		
   		function backup(count, path) {
   			if (!count) count = 0; 
   			value = prompt("Please specify the number of replicas", count);
   			if (value != null) {
	   			if (value < 0) {
	   				alert("Must supply a positive number: " + value);
	   			}
	   			else if (value != count) {
	   			   window.location = path + "?command=backup&count=" + value;
	   			}
	   			else {
	   				alert("Already scheduled to backup to " + value + " replicas");
	   			}
	   		}
   		}
		]]>
	</script>

  </head>

  <body>
 
    <h2>File Sync: Distributed Software Development (<xsl:value-of select='/Devices/@localdevice'/>)</h2>

	<center>
	    <div id="box">		
			<div class="header">
				<form>
					<div class="menu">
						<ul>
							<li><a href="javascript:fileview()" title="Show file listing"><span>File View</span></a></li>
							<li><a href="javascript:settings()" title="Change settings"><span>Settings</span></a></li>
							<li><a href="javascript:logout()" title="Logout"><span>Logout</span></a></li>
						</ul>
					</div>
					<div class="search">
						Search Files <input id="searchInput" type="text"/><button type="button" onClick="javascript:search(searchInput.value)" title="Search filenames containing your text">OK</button>
					</div>
				</form>
			</div>

			<div class="main">
				<table class="files">
					<tr>
						<th width="40px"></th>
						<th>Name</th>
						<th width="100px">Action</th>
						<th width="70px">Size</th>
						<th width="110px">Modified (<a href="javascript:searchModified()" title="Search by modified date">search</a>)</th>
					</tr>
					<tr><th colspan="5"><hr/></th></tr>

					<xsl:for-each select="/Devices/Device">
					  <tr class="devicerow">
						<td colspan="5">
						  <a>
					  	    <xsl:attribute name="href"><xsl:choose><xsl:when test="Name = 'Google Documents'">javascript:gotoDevice("gdocs")</xsl:when><xsl:otherwise>javascript:gotoDevice('<xsl:value-of select="Name"/>')</xsl:otherwise></xsl:choose></xsl:attribute>
							<xsl:value-of select="Name" />
						  </a>
						  (<xsl:value-of select="OnlineStatus" />)
							  	
						  <xsl:if test="URL">
						  	<a>
						  	  <xsl:attribute name="href"><xsl:value-of select="URL"/>/login</xsl:attribute>
						  	  &gt;&gt;
						  	</a>
						  </xsl:if>
					    </td>
					  </tr>
							
					  <xsl:for-each select="FileList/File">
					     <xsl:choose>
					        <xsl:when test="position() mod 2 = 0">
								<tr class="evenrow">
								  <td><img src="/all/html/file/images/file.gif"/></td>
								  <td>
								    <xsl:choose>
								      <xsl:when test="Deleted = 'yes'">
								        <xsl:value-of select="Name"/>
								        (deleted)
								      </xsl:when>
								      <xsl:otherwise>
								        <xsl:choose>
								          <xsl:when test="../../OnlineStatus = 'online'">
										    <a>
										      <xsl:attribute name="href"><xsl:choose><xsl:when test="URL"><xsl:value-of select="URL"/></xsl:when><xsl:otherwise><xsl:value-of select="../../URL"/>/<xsl:value-of select="../../Name"/>/html/file/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/></xsl:otherwise></xsl:choose></xsl:attribute>
										      <xsl:value-of select="Name"/>
										    </a>
										  </xsl:when>
										  <xsl:otherwise>
										    <xsl:value-of select="Name"/>
										  </xsl:otherwise>
										</xsl:choose>
									  </xsl:otherwise>
									</xsl:choose>
								  </td>
								  <td>
								    <xsl:if test="/Devices/@localdevice = ../../Name">
								      <xsl:choose>
								        <xsl:when test="Deleted = 'yes'">
								          <xsl:if test="Backup/Location">
								            <a>
								              <xsl:attribute name="href">/backupdata/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/>?command=restore</xsl:attribute>
								              <xsl:attribute name="title">Restore this file from its backup</xsl:attribute>
								              restore
								            </a>
								          </xsl:if>
									    </xsl:when>
									    <xsl:otherwise>
									      <a>
									      	<xsl:attribute name="href">/html/gdocsupload/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/></xsl:attribute>
									      	<xsl:attribute name="title">Upload this file to Google Documents</xsl:attribute>
										    upload,
									      </a>
									      <a>
									        <xsl:attribute name="href">javascript:backup('<xsl:value-of select="Backup/@count"/>', '/backupdata/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/>')</xsl:attribute>
									        <xsl:attribute name="title">Take backup of this document</xsl:attribute>
									        backup
									      </a>
									    </xsl:otherwise>
									  </xsl:choose>
								    </xsl:if>
								  </td>
								  <td><xsl:value-of select="Size"/></td>
								  <td><script type="text/javascript"><![CDATA[var d=new Date(); d.setTime(]]><xsl:value-of select="LastModified"/><![CDATA[); document.write(d.toDateString());]]></script></td>
								</tr>
					        </xsl:when>
					        <xsl:otherwise>
								<tr class="oddrow">
								  <td><img src="/all/html/file/images/file.gif"/></td>
								  <td>
								    <xsl:choose>
								      <xsl:when test="Deleted = 'yes'">
								        <xsl:value-of select="Name"/>
								        (deleted)
								      </xsl:when>
								      <xsl:otherwise>
								        <xsl:choose>
								          <xsl:when test="../../OnlineStatus = 'online'">
										    <a>
										      <xsl:attribute name="href"><xsl:choose><xsl:when test="URL"><xsl:value-of select="URL"/></xsl:when><xsl:otherwise><xsl:value-of select="../../URL"/>/<xsl:value-of select="../../Name"/>/html/file/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/></xsl:otherwise></xsl:choose></xsl:attribute>
										      <xsl:value-of select="Name"/>
										    </a>
										  </xsl:when>
										  <xsl:otherwise>
										    <xsl:value-of select="Name"/>
										  </xsl:otherwise>
										</xsl:choose>
									  </xsl:otherwise>
									</xsl:choose>
								  </td>
								  <td>
								    <xsl:if test="/Devices/@localdevice = ../../Name">
								      <xsl:choose>
								        <xsl:when test="Deleted = 'yes'">
								          <xsl:if test="Backup/Location">
								            <a>
								              <xsl:attribute name="href">/backupdata/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/>?command=restore</xsl:attribute>
								              <xsl:attribute name="title">Restore this file from its backup</xsl:attribute>
								              restore
								            </a>
								          </xsl:if>
									    </xsl:when>
									    <xsl:otherwise>
									      <a>
									      	<xsl:attribute name="href">/html/gdocsupload/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/></xsl:attribute>
									      	<xsl:attribute name="title">Upload this file to Google Documents</xsl:attribute>
										    upload,
									      </a>
									      <a>
									        <xsl:attribute name="href">javascript:backup('<xsl:value-of select="Backup/@count"/>', '/backupdata/<xsl:value-of select="Path"/><xsl:if test="string(Path)">/</xsl:if><xsl:value-of select="Name"/>')</xsl:attribute>
									        <xsl:attribute name="title">Take backup of this document</xsl:attribute>
									        backup
									      </a>
									    </xsl:otherwise>
									  </xsl:choose>
								    </xsl:if>
								  </td>
								  <td><xsl:value-of select="Size"/></td>
								  <td><script type="text/javascript"><![CDATA[var d=new Date(); d.setTime(]]><xsl:value-of select="LastModified"/><![CDATA[); document.write(d.toDateString());]]></script></td>
								</tr>
					        </xsl:otherwise>
					     </xsl:choose>
					   </xsl:for-each>
					</xsl:for-each>
					
					<!-- put img src="images/folder.gif" or"images/file.gif" -->
				</table>
			</div>

			<div class="footer">
				<!--
				<form>
					<div class="menu">
						<ul>
							<li> Select device</li>
							<li><a href="javascript:gotoDevice('all')" title="All files"><span>all</span></a></li>
							<li><a href="javascript:gotoDevice('localhost')" title="Local files"><span>localhost</span></a></li>
							<li><a href="javascript:gotoDevice('gdocs')" title="Google documents"><span>gdocs</span></a></li>
						</ul>
					</div>
				</form>
				-->
			</div>
		</div>		
	</center>
  </body>
</html>

</xsl:template>
</xsl:stylesheet>