<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <meta name="layout" content="main"/>
    <title></title>
</head>

<body>
<g:if test="${flash.message}">
    <div>
        <g:applyCodec encodeAs="HTML">${flash.message.decodeHTML()}</g:applyCodec><br/>
    </div>
</g:if>

<g:if test="${flash.warn}">
    <div>
        <g:applyCodec encodeAs="HTML">${flash.warn.decodeHTML()}</g:applyCodec><br/>
    </div>
</g:if>

<g:if test="${flash.error}">
    <div>
        <g:applyCodec encodeAs="HTML">${flash.error.decodeHTML()}</g:applyCodec><br/>
    </div>
</g:if>
Welcome to Mail OAuth Plugin
</body>
</html>