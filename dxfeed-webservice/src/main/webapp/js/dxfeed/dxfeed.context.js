/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */

//<%@ page contentType="application/x-javascript" %>
// setup webservice context path via server-side JSP code so that dxFeed can automatically connect
(window.dx = window.dx || {}).contextPath = "<%= request.getContextPath() %>";
