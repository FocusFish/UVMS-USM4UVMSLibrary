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

package fish.focus.uvms.rest.security.bean;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.transaction.Transactional;
import javax.xml.bind.JAXBException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import fish.focus.uvms.commons.message.impl.JAXBUtils;
import fish.focus.uvms.user.model.exception.ModelMarshallException;
import fish.focus.uvms.user.model.mapper.UserModuleRequestMapper;
import fish.focus.wsdl.user.module.CreateDatasetResponse;
import fish.focus.wsdl.user.module.DeleteDatasetResponse;
import fish.focus.wsdl.user.module.DeployApplicationResponse;
import fish.focus.wsdl.user.module.FilterDatasetResponse;
import fish.focus.wsdl.user.module.GetDeploymentDescriptorRequest;
import fish.focus.wsdl.user.module.GetDeploymentDescriptorResponse;
import fish.focus.wsdl.user.module.GetUserContextResponse;
import fish.focus.wsdl.user.module.PutPreferenceResponse;
import fish.focus.wsdl.user.module.RedeployApplicationResponse;
import fish.focus.wsdl.user.module.UserModuleMethod;
import fish.focus.wsdl.user.types.Application;
import fish.focus.wsdl.user.types.Context;
import fish.focus.wsdl.user.types.Dataset;
import fish.focus.wsdl.user.types.DatasetExtension;
import fish.focus.wsdl.user.types.DatasetFilter;
import fish.focus.wsdl.user.types.Feature;
import fish.focus.wsdl.user.types.Option;
import fish.focus.wsdl.user.types.Preference;
import fish.focus.wsdl.user.types.UserContext;
import fish.focus.wsdl.user.types.UserContextId;
import fish.focus.wsdl.user.types.UserFault;
import fish.focus.wsdl.user.types.UserPreference;
import fish.focus.uvms.exception.ServiceException;
import fish.focus.uvms.jms.USMMessageConsumer;
import fish.focus.uvms.jms.USMMessageProducer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class USMServiceBean implements USMService {

    private static final Logger LOG = LoggerFactory.getLogger(USMServiceBean.class);
    private static final Long UVMS_USM_TIMEOUT = 30000L;

    @Inject
    private USMMessageProducer messageProducer;

    @Inject
    private USMMessageConsumer messageConsumer;

    @Override
    public String getOptionDefaultValue(String optionName, String applicationName) throws ServiceException {
        LOG.debug("START getOptionDefaultValue({}, {})", optionName, applicationName);
        Application application = getApplicationDefinition(applicationName);
        String defaultOptionValue = null;
        List<Option> allOptions = application.getOption();
        for (Option opt : allOptions) {
            if (opt.getName().equalsIgnoreCase(optionName)) {
                defaultOptionValue = opt.getDefaultValue();
                break;
            }
        }
        return defaultOptionValue;
    }

    @Override
    public Context getUserContext(String username, String applicationName, String currentRole, String currentScope) throws ServiceException {
        LOG.debug("START getUserContext({}, {}, {}, {})", username, applicationName, currentRole, currentScope);
        Context context = null;
        UserContext fullContext = getFullUserContext(username, applicationName);
        if (fullContext != null) {
            for (Context usmCtx : fullContext.getContextSet().getContexts()) {
                if (isContextMatch(usmCtx, currentRole, currentScope)) {
                   context = usmCtx;
                   break;
                }
            }
        }
        if (context == null) {
            throw new ServiceException("Context with the provided username, role and scope is not found.");
        }
        return context;
    }

    @Override
    public String getUserPreference(String preferenceName, String username, String applicationName, String currentRole, String currentScope) throws ServiceException {
        LOG.debug("START getUserPreference({}, {}, {}, {}, {})", preferenceName, username, applicationName, currentRole, currentScope);
        String userPrefValue;
        Context userContext = getUserContext(username, applicationName, currentRole, currentScope);
        userPrefValue = getUserPreference(preferenceName, userContext);
        return userPrefValue;
    }

    @Override
    public String getUserPreference(String preferenceName, Context userContext) throws ServiceException {
        String userPrefValue = null;
        if (userContext != null) {
            if (userContext.getPreferences() != null) {
                List<Preference> listPrefs = userContext.getPreferences().getPreference();
                for (Preference pref : listPrefs) {
                    if (pref.getOptionName().equals(preferenceName)) {
                        userPrefValue = pref.getOptionValue();
                        break;
                    }
                }
            }
        }
        return userPrefValue;
    }

    @Override
    public Application getApplicationDefinition(String applicationName) throws ServiceException {
        LOG.debug("START getApplicationDefinition({})", applicationName);
        Application application = null;
        GetDeploymentDescriptorRequest getDeploymentDescriptorRequest = new GetDeploymentDescriptorRequest();
        getDeploymentDescriptorRequest.setMethod(UserModuleMethod.GET_DEPLOYMENT_DESCRIPTOR);
        getDeploymentDescriptorRequest.setApplicationName(applicationName);
        try {
            String msgId = messageProducer.sendMessage(JAXBUtils.marshallJaxBObjectToString(getDeploymentDescriptorRequest), messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", msgId);
            String response = messageConsumer.getMessageBody(msgId, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                GetDeploymentDescriptorResponse getDeploymentDescriptorResponse = JAXBUtils.unMarshallMessage(response, GetDeploymentDescriptorResponse.class);
                LOG.debug("Response concerning message with ID: {} is received.", msgId);
                application = getDeploymentDescriptorResponse.getApplication();
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", msgId);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                    throw new ServiceException("Unable to receive a response from USM.");
                }
            }
        } catch (JMSException | JAXBException e) {
            throw new ServiceException("Unable to get Application Definition", e);
        }
        return application;
    }

    @Override
    @Transactional
    public void deployApplicationDescriptor(Application descriptor) throws ServiceException {
        LOG.debug("START deployApplicationDescriptor({})", descriptor);
        try {
            String descriptorString = UserModuleRequestMapper.mapToDeployApplicationRequest(descriptor);
            String msgId = messageProducer.sendMessage(descriptorString, messageConsumer.getDestination());
            String response = messageConsumer.getMessageBody(msgId, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                DeployApplicationResponse deployApplicationResponse = JAXBUtils.unMarshallMessage(response, DeployApplicationResponse.class);
                LOG.debug("Response concerning message with ID: {} is received.", msgId);
                if ("OK".equalsIgnoreCase(deployApplicationResponse.getResponse())) {
                    LOG.info("Application successfully registered into USM.");
                } else {
                    throw new ServiceException("Unable to register into USM.");
                }
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", msgId);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                    throw new ServiceException("Unable to register into USM.");
                } else {
                    throw new ServiceException("Unable to register into USM.");
                }
            }
        } catch (JMSException | JAXBException | ModelMarshallException e) {
            throw new ServiceException("Unable to deploy Application descriptor", e);
        }
    }

    @Override
    public void redeployApplicationDescriptor(Application deploymentDescriptor) throws ServiceException {
        LOG.debug("START redeployApplicationDescriptor({})", deploymentDescriptor);
        try {
            String descriptorString = UserModuleRequestMapper.mapToRedeployApplicationRequest(deploymentDescriptor);
            String msgId = messageProducer.sendMessage(descriptorString, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", msgId);

            String response = messageConsumer.getMessageBody(msgId, String.class, UVMS_USM_TIMEOUT);

            if (response != null && !isUserFault(response)) {
                RedeployApplicationResponse redeployApplicationResponse = JAXBUtils.unMarshallMessage(response, RedeployApplicationResponse.class);
                LOG.debug("Response concerning message with ID: {} is received.", msgId);
                if ("OK".equalsIgnoreCase(redeployApplicationResponse.getResponse())) {
                    LOG.info("Application successfully registered into USM.");
                } else {
                    throw new ServiceException("Unable to register into USM.");
                }
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", msgId);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                    throw new ServiceException("Unable to register into USM.");
                } else {
                    throw new ServiceException("Unable to register into USM.");
                }
            }
        } catch (JMSException | JAXBException | ModelMarshallException e) {
            throw new ServiceException("Unable to deploy Application descriptor", e);
        }
    }


    @Override
    @Transactional
    public void setOptionDefaultValue(String keyOption, String defaultValue, String applicationName) throws ServiceException {
        LOG.debug("START setOptionDefaultValue({}, {}, {})", keyOption, defaultValue, applicationName);
        Application application = getApplicationDefinition(applicationName);
        List<Option> optionList = application.getOption();
        boolean isOptionAdd = true;
        for (Option option : optionList) {
            if (option.getName().equals(keyOption)) {
                isOptionAdd = false;
                option.setDefaultValue(defaultValue);
                break;
            }
        }
        if (isOptionAdd) {
            Option option = new Option();
            option.setName(keyOption);
            option.setDefaultValue(defaultValue);
            application.getOption().add(option);
        }
        redeployApplicationDescriptor(application);
    }

    @Override
    @Transactional
    public void putUserPreference(String keyOption, String userDefinedValue, String applicationName, String scopeName, String roleName, String username) throws ServiceException {
        LOG.debug("START putUserPreference({} , {}, {}, {}, {}, {})", keyOption, userDefinedValue, applicationName, scopeName, roleName, username);
        UserPreference userPreference = new UserPreference();
        userPreference.setApplicationName(applicationName);
        userPreference.setOptionName(keyOption);
        userPreference.setOptionValue(userDefinedValue.getBytes());
        userPreference.setScopeName(scopeName);
        userPreference.setUserName(username);
        userPreference.setRoleName(roleName);
        putUserPreference(userPreference);
    }

    private void putUserPreference(UserPreference userPreference) throws ServiceException {
        LOG.debug("START putUserPreference param: {}", userPreference);
        String payload;
        try {
            payload = UserModuleRequestMapper.mapToPutUserPreferenceRequest(userPreference);
            String messageID = messageProducer.sendMessage(payload, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is successfully sent to USM.", messageID);
            String response = messageConsumer.getMessageBody(messageID, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                PutPreferenceResponse putPreferenceResponse = JAXBUtils.unMarshallMessage(response, PutPreferenceResponse.class);
                LOG.debug("Response concerning message with ID: {} is received. The response is: {}", messageID, putPreferenceResponse.getResponse());
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", messageID);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                }
            }
        } catch (ModelMarshallException | JMSException | JAXBException e) {
            throw new ServiceException("Unable to set user preference into USM.", e);
        }
        LOG.debug("END putUserPreference");
    }


    @Override
    public List<Dataset> getDatasetsPerCategory(String category, String username, String applicationName, String currentRole, String currentScope) throws ServiceException {
        Context ctxt = getUserContext(username, applicationName, currentRole, currentScope);
        return getDatasetsPerCategory(category, ctxt);
    }

    @Override
    public List<Dataset> getDatasetsPerCategory(String category, Context userContext) throws ServiceException {
        LOG.debug("START getDatasetsPerCategory({}, {})", category, userContext);
        List<Dataset> filteredDatasets = new LinkedList<>();
        if (userContext != null) {
            List<Dataset> datasetList = userContext.getScope().getDataset();

            for (Dataset dataset : datasetList) {
                if (dataset.getCategory().equalsIgnoreCase(category)) {
                    filteredDatasets.add(dataset);
                }
            }
        }
        LOG.debug("END getDatasetsPerCategory(...), return {} datasets.", filteredDatasets.size());
        return filteredDatasets;
    }

    @Override
    @Transactional
    public void createDataset(String applicationName, String datasetName, String discriminator, String category, String description) throws ServiceException {
        LOG.debug("START createDataset({}, {}, {}, {}, {})", applicationName, datasetName, discriminator, category, description);
        if (StringUtils.isEmpty(applicationName) || StringUtils.isEmpty(datasetName)) {
            throw new IllegalArgumentException("Application name, nor dataset name cannot be null");
        }
        try {
            DatasetExtension dataset = new DatasetExtension();
            dataset.setApplicationName(applicationName);
            dataset.setDiscriminator(discriminator);
            dataset.setName(datasetName);
            dataset.setCategory(category);
            dataset.setDescription(description);
            String payload = UserModuleRequestMapper.mapToCreateDatasetRequest(dataset);
            String messageID = messageProducer.sendMessage(payload, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", messageID);
            String response = messageConsumer.getMessageBody(messageID, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                CreateDatasetResponse createDatasetResponse = JAXBUtils.unMarshallMessage(response, CreateDatasetResponse.class);
                LOG.debug("Response concerning message with ID: {} is received. The response is: {}", messageID, createDatasetResponse.getResponse());
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", messageID);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                }
            }
        } catch (ModelMarshallException | JMSException | JAXBException e) {
            throw new ServiceException("Unable to update Dataset.", e);
        }
    }


    @Override
    public void deleteDataset(String applicationName, String datasetName) throws ServiceException {
        LOG.debug("START deleteDataset({}, {}", applicationName, datasetName);
        try {
            DatasetExtension dataset = new DatasetExtension();
            dataset.setApplicationName(applicationName);
            dataset.setName(datasetName);
            String payload = UserModuleRequestMapper.mapToDeleteDatasetRequest(dataset);
            String messageID = messageProducer.sendMessage(payload, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", messageID);
            String response = messageConsumer.getMessageBody(messageID, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                DeleteDatasetResponse deleteDatasetResponse = JAXBUtils.unMarshallMessage(response, DeleteDatasetResponse.class);
                LOG.debug("Response concerning message with ID: {} is received. The response is: {}", messageID, deleteDatasetResponse.getResponse());
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", messageID);
                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                }
            }
        } catch (ModelMarshallException | JMSException | JAXBException e) {
            throw new ServiceException("Unable to update Dataset.", e);
        }
    }


    @Override
    public List<DatasetExtension> findDatasetsByDiscriminator(String applicationName, String discriminator) throws ServiceException {
        LOG.debug("START findDatasetByDiscriminator({}, {}", applicationName, discriminator);
        List<DatasetExtension> listToReturn = null;
        try {
            DatasetFilter datasetFilter = new DatasetFilter();
            datasetFilter.setApplicationName(applicationName);
            datasetFilter.setDiscriminator(discriminator);
            String payload = UserModuleRequestMapper.mapToFindDatasetRequest(datasetFilter);
            String messageID = messageProducer.sendMessage(payload, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", messageID);
            String response = messageConsumer.getMessageBody(messageID, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                FilterDatasetResponse filterDatasetResponse = JAXBUtils.unMarshallMessage(response, FilterDatasetResponse.class);
                LOG.debug("Response concerning message with ID: {} is received. The response is: {}", messageID, filterDatasetResponse.getDatasetList().getList());
                listToReturn = filterDatasetResponse.getDatasetList().getList();
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", messageID);

                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                }
            }
        } catch (ModelMarshallException | JMSException | JAXBException e) {
            throw new ServiceException("Unable to update Dataset.", e);
        }
        LOG.debug("END findDatasetByDiscriminator(...), returning {}", listToReturn);
        return listToReturn;
    }


    @Override
    public UserContext getFullUserContext(String remoteUser, String applicationName) throws ServiceException {
        LOG.debug("START getFullUserContext({}, {})", remoteUser, applicationName);
        UserContext userContext = null;
        UserContextId contextId = new UserContextId();
        contextId.setApplicationName(applicationName);
        contextId.setUserName(remoteUser);
        String payload;
        try {
            payload = UserModuleRequestMapper.mapToGetUserContextRequest(contextId);
            String messageID = messageProducer.sendMessage(payload, messageConsumer.getDestination());
            LOG.debug("JMS message with ID: {} is sent to USM.", messageID);
            String response = messageConsumer.getMessageBody(messageID, String.class, UVMS_USM_TIMEOUT);
            if (response != null && !isUserFault(response)) {
                GetUserContextResponse userContextResponse = JAXBUtils.unMarshallMessage(response, GetUserContextResponse.class);
                LOG.debug("Response concerning message with ID: {} is received.", messageID);
                userContext = userContextResponse.getContext();
            } else {
                LOG.error("Error occurred while receiving JMS response for message ID: {}", messageID);
                if (response != null) {
                    UserFault error = JAXBUtils.unMarshallMessage(response, UserFault.class);
                    LOG.error("Error Code: {}, Message: {}", error.getCode(), error.getFault());
                    throw new ServiceException("Unable to receive a response from USM.");
                }
            }
        } catch (ModelMarshallException | JMSException | JAXBException e) {
            throw new ServiceException("Unexpected exception while trying to get user context.", e);
        }
        return userContext;
    }

    @Override
    public Set<String> getUserFeatures(String username, Context userContext) throws ServiceException {
        LOG.debug("START getUserFeatures({} ,{})", username, userContext);
        if (userContext == null) {
            return null;
        }
        List<Feature> features = userContext.getRole().getFeature();
        Set<String> featuresStr = new HashSet<>(features.size());
        //extract only the features that the particular application is interested in
        for (Feature feature : features) {
            featuresStr.add(feature.getName());
        }
        LOG.debug("END getUserFeatures(...), returns {} ", featuresStr);
        return featuresStr;
    }

    @Override
    public Set<String> getUserFeatures(String username, String applicationName, String currentRole, String currentScope) throws ServiceException {
        Context ctxt = getUserContext(username, applicationName, currentRole, currentScope);
        return getUserFeatures(username, ctxt);
    }

    private boolean isUserFault(String message) {
        boolean isErrorResponse = false;
        try {
            JAXBUtils.unMarshallMessage(message, UserFault.class);
            isErrorResponse = true;
        } catch (JAXBException e) {
            //do nothing  since it's not a UserFault
        }
        return isErrorResponse;
    }


    private boolean isContextMatch(Context usmCtx, String currentRole, String currentScope) {
        boolean isContextMatch = false;
        if (usmCtx.getRole().getRoleName().equalsIgnoreCase(currentRole)) {
            isContextMatch = true;
        }
        //check if our user has a scope (it is possible to have a context without a scope)
        if (StringUtils.isNotBlank(currentScope)) {
            if (usmCtx.getScope() == null || !usmCtx.getScope().getScopeName().equalsIgnoreCase(currentScope)) {
                isContextMatch = false;
            }
        }
        return isContextMatch;
    }

}