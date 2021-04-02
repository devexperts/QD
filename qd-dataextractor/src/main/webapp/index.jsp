<%--
  !++
  QDS - Quick Data Signalling Library
  !-
  Copyright (C) 2002 - 2021 Devexperts LLC
  !-
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  http://mozilla.org/MPL/2.0/.
  !__
  --%>
<%@ page import="com.devexperts.qd.QDFactory" %>
<!DOCTYPE html>
<html>
<head>
<title>Welcome @ Data Extractor</title>
</head>
<body>
<h1>Welcome @ Data Extractor</h1>
<p>Version: <%= QDFactory.getVersion() %>
<p><a href="data?help">Read help here</a>
</body>
</html>
