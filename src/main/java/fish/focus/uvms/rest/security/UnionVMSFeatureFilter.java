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
package fish.focus.uvms.rest.security;

import fish.focus.uvms.usm.jwt.JwtTokenHandler;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.ejb.EJB;
import javax.faces.context.ExceptionHandler;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class UnionVMSFeatureFilter extends AbstractUSMHandler implements ContainerRequestFilter {

    @EJB
    private JwtTokenHandler jwtTokenHandler;

    @Context
    private ResourceInfo resourceInfo;


    @Override
    public void filter(ContainerRequestContext requestContext) {
        UnionVMSFeature feature;
        if (resourceInfo.getResourceMethod().isAnnotationPresent(RequiresFeature.class)) {
            feature = resourceInfo.getResourceMethod().getAnnotation(RequiresFeature.class).value();
        } else if (resourceInfo.getResourceClass().isAnnotationPresent(RequiresFeature.class)) {
            feature = resourceInfo.getResourceClass().getAnnotation(RequiresFeature.class).value();
        } else {
            return;
        }

        try {
            String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
            if(authorizationHeader == null || authorizationHeader.isEmpty()){
                sendAccessForbidden(requestContext);
                return;
            }

            List<Integer> featuresThisUserHas = jwtTokenHandler.parseTokenFeatures(authorizationHeader);
            if (featuresThisUserHas == null || featuresThisUserHas.stream().noneMatch(f -> f.equals(feature.getFeatureId()))) {
                sendAccessForbidden(requestContext);
                return;
            }
        }catch(Exception e){
            requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                    .entity(ExceptionUtils.getRootCause(e)).build());
            return;

        }
    }


    private void sendAccessForbidden(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .entity("User cannot access the resource.").build());
    }

}