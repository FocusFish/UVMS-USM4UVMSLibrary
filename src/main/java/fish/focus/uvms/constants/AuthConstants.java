/*
﻿Developed with the contribution of the European Commission - Directorate General for Maritime Affairs and Fisheries
© European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or any later version. The IFDM Suite is distributed in
the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have received a
copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 */
package fish.focus.uvms.constants;

import javax.ws.rs.core.HttpHeaders;

/**
 * Created by georgige on 10/2/2015.
 */
public interface AuthConstants {

    String HTTP_HEADER_ROLE_NAME = "roleName";
    String HTTP_HEADER_SCOPE_NAME = "scopeName";
    String HTTP_HEADER_AUTHORIZATION = HttpHeaders.AUTHORIZATION;
    String HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    String JWTCALLBACK = "jwtcallback";

    String HTTP_SERVLET_CONTEXT_ATTR_FEATURES = "servletContextUserFeatures";

    String HTTP_SESSION_ATTR_ROLES_NAME = HTTP_SERVLET_CONTEXT_ATTR_FEATURES;

    String CACHE_NAME_USER_SESSION = "userSessionCache";
    String CACHE_NAME_APP_MODULE = "appModuleCache";
}