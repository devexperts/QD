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
<%@ page import="com.dxfeed.event.EventType" %>
<%@ page import="com.dxfeed.webservice.DXFeedContext" %>
<%@ page import="com.dxfeed.webservice.DXFeedJson" %>
<%@ page import="java.util.Map" %>
<%
// automatically generate section for each supported event type and property
    for (Map.Entry<DXFeedContext.Group, Map<String, Class<? extends EventType<?>>>> gEntry : DXFeedContext.INSTANCE.getGroupedEventTypes().entrySet()) {
        DXFeedContext.Group group = gEntry.getKey();
%><p><span style="font-weight: bold"><%= group.title %></span> (see <a href="<%= group.seeHRef %>"><%= group.seeName %></a>)<%
        for (Class<? extends EventType<?>> typeClass : gEntry.getValue().values()) {
            String typeName = typeClass.getSimpleName();
            StringBuilder props = new StringBuilder();
            for (String s : DXFeedJson.getProperties(typeClass)) {
                if (s.equals("eventSymbol"))
                    continue;
                if (props.length() > 0)
                    props.append(",");
                props.append(s);
            }
%><label class="type">
    <input type="checkbox" name="<%= typeName %>" value="true" dx-props="<%= props %>" dx-group="<%= group %>">
    <%= typeName %> (see <a href="javadoc/<%= typeClass.getName().replace('.', '/') %>.html"><%= typeName %></a>)
</label><%
        }
    }
%>
