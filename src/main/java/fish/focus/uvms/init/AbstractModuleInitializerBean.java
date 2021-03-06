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
package fish.focus.uvms.init;

import fish.focus.wsdl.user.module.DeployApplicationRequest;
import fish.focus.wsdl.user.types.Application;
import fish.focus.uvms.exception.ServiceException;
import fish.focus.uvms.rest.security.bean.USMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Schedule;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.util.Iterator;

public abstract class AbstractModuleInitializerBean {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractModuleInitializerBean.class);

    private static final Long UVMS_USM_TIMEOUT = 10000L;

    private int count = 0;

    @EJB
    private USMService usmService;

    @Resource
    private TimerService timerService;

    @Schedule(minute = "*", hour = "*", persistent = false, info = "AUTO_TIMER_0")
    public void atSchedule() throws JAXBException, ServiceException {
        try {
            if (count < 5) {
                // do something on application startup
                InputStream deploymentDescInStream = getDeploymentDescriptorRequest();
                if (deploymentDescInStream != null) {
                    JAXBContext jaxBcontext = JAXBContext.newInstance(DeployApplicationRequest.class);
                    javax.xml.bind.Unmarshaller um = jaxBcontext.createUnmarshaller();
                    DeployApplicationRequest applicationDefinition = (DeployApplicationRequest) um.unmarshal(deploymentDescInStream);
                    if (!isAppDeployed(applicationDefinition.getApplication())) {
                        usmService.deployApplicationDescriptor(applicationDefinition.getApplication());
                    } else if (mustRedeploy()) {
                        usmService.redeployApplicationDescriptor(applicationDefinition.getApplication());
                    }
                } else {
                    LOG.error("USM deployment descriptor is not provided, therefore, the JMS deployment message cannot be sent.");
                }
                stopTimer(); // Stop timer as there is no exception and communication to USM is successful
            }
        } catch (ServiceException e) {
            count++;
            LOG.info("Failed to connect to USM. Retry count " + count);
            if (count == 5) { // Stop timer after 5 retry
                stopTimer();
                throw new ServiceException("Deployment failed : Could not connect to USM");
            }
        }
    }

    private void stopTimer() {
        Iterator<Timer> timerIterator = timerService.getTimers().iterator();
        Timer timerToCancel = null;
        while (timerIterator.hasNext()) {
            Timer tmpTimer = timerIterator.next();
            if (tmpTimer.getInfo().equals("AUTO_TIMER_0")) {
                timerToCancel = tmpTimer;
                break;
            }
        }
        if (timerToCancel != null) {
            timerToCancel.cancel();
        }
    }

    private boolean isAppDeployed(Application deploymentDescriptor) throws ServiceException {
        boolean isAppDeployed = false;
        Application application = usmService.getApplicationDefinition(deploymentDescriptor.getName());
        if (application != null) {
            isAppDeployed = true;
        }
        return isAppDeployed;
    }

    /**
     * @return InputStream with the String representation of Application descriptor
     */
    protected abstract InputStream getDeploymentDescriptorRequest();

    /**
     * @return true if the application descriptor must be redeployed
     */
    protected abstract boolean mustRedeploy();
}