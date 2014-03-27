/*
* JBoss, a division of Red Hat
* Copyright 2006, Red Hat Middleware, LLC, and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.picketlink.idm.impl.repository;

import org.picketlink.idm.impl.api.IdentitySearchCriteriaImpl;
import org.picketlink.idm.impl.types.SimpleIdentityObject;
import org.picketlink.idm.spi.configuration.metadata.IdentityStoreMappingMetaData;
import org.picketlink.idm.spi.store.IdentityStore;
import org.picketlink.idm.spi.store.AttributeStore;
import org.picketlink.idm.spi.store.FeaturesMetaData;
import org.picketlink.idm.spi.store.IdentityStoreInvocationContext;
import org.picketlink.idm.spi.store.IdentityStoreSession;
import org.picketlink.idm.spi.store.IdentityObjectSearchCriteriaType;
import org.picketlink.idm.spi.model.IdentityObject;
import org.picketlink.idm.spi.model.IdentityObjectType;
import org.picketlink.idm.spi.model.IdentityObjectRelationshipType;
import org.picketlink.idm.spi.model.IdentityObjectRelationship;
import org.picketlink.idm.spi.exception.OperationNotSupportedException;
import org.picketlink.idm.spi.configuration.metadata.IdentityRepositoryConfigurationMetaData;
import org.picketlink.idm.spi.configuration.metadata.IdentityObjectAttributeMetaData;
import org.picketlink.idm.spi.configuration.IdentityRepositoryConfigurationContext;
import org.picketlink.idm.spi.configuration.IdentityStoreConfigurationContext;
import org.picketlink.idm.spi.model.IdentityObjectCredential;
import org.picketlink.idm.spi.model.IdentityObjectCredentialType;
import org.picketlink.idm.spi.model.IdentityObjectAttribute;
import org.picketlink.idm.spi.search.IdentityObjectSearchCriteria;
import org.picketlink.idm.common.exception.IdentityException;
import org.picketlink.idm.impl.store.SimpleIdentityStoreInvocationContext;
import org.picketlink.idm.impl.api.session.managers.RoleManagerImpl;

import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>In FallbackIdentityStoreRepository one IdentityStore plays the role of default store. Any operation that cannot be
 * handled with other IdentityObjectType/IdentityStore mappings will fallback to such IdentityStore. The most common example
 * is RDBMS + LDAP configuration. LDAP has limmited schema for possible profile attributes so for LDAP entries part of
 * profile can be stored in RDBMS by syncing entries into default store.</p>
 * <p>For any relationship that is not supported in other stores, or between entries persisted in two different stores,
 * proper IdentityObjects will be synced to default store and if possible, such relationship will be created. </p>
 *
 *
 * @author <a href="mailto:boleslaw.dawidowicz at redhat.com">Boleslaw Dawidowicz</a>
 * @version : 0.1 $
 */
public class FallbackIdentityStoreRepository extends AbstractIdentityStoreRepository
{
   //TODO: - filter out criteria based on features MD before passing
   //TODO: - configuration option to store not mapped attributes in default store
   //TODO: - configuration option to fallback named relationships to default store when not supported in mapped one

   private static Logger log = Logger.getLogger(FallbackIdentityStoreRepository.class.getName());

   public static final String OPTION_READ_ONLY = "readOnly";

   private final String id;

   //TODO: rewrite this to other config object?
   @SuppressWarnings("unused")
   private IdentityRepositoryConfigurationMetaData configurationMD;

   public static final String ALLOW_NOT_DEFINED_ATTRIBUTES = "allowNotDefinedAttributes";

   private FeaturesMetaData featuresMetaData;
   
   private boolean allowNotDefinedAttributes = false;

   private final Set<IdentityStore> configuredIdentityStores = new HashSet<IdentityStore>();

   public FallbackIdentityStoreRepository(String id)
   {
      this.id = id;
   }

   @Override
   public void bootstrap(IdentityRepositoryConfigurationContext configurationContext,
                         Map<String, IdentityStore> bootstrappedIdentityStores,
                         Map<String, AttributeStore> bootstrappedAttributeStores) throws IdentityException
   {
      super.bootstrap(configurationContext, bootstrappedIdentityStores, bootstrappedAttributeStores);

      // Helper collection to keep all identity stores in use

      if (getIdentityStoreMappings().size() > 0)
      {
         configuredIdentityStores.addAll(getMappedIdentityStores());
      }
      


      this.configurationMD = configurationContext.getRepositoryConfigurationMetaData();

      String isId = configurationMD.getDefaultIdentityStoreId();

      if (isId != null && bootstrappedIdentityStores.keySet().contains(isId))
      {
         if (!getIdentityStoreMappings().keySet().contains(defaultIdentityStore.getId()))
         {
            configuredIdentityStores.add(defaultIdentityStore);
         }

      }

      String allowNotDefineAttributes = configurationMD.getOptionSingleValue(ALLOW_NOT_DEFINED_ATTRIBUTES);

      if (allowNotDefineAttributes != null && allowNotDefineAttributes.equalsIgnoreCase("true"))
      {
         this.allowNotDefinedAttributes = true;
      }

      // A wrapper around all stores features meta data
      featuresMetaData = new FeaturesMetaData()
      {

         public boolean isNamedRelationshipsSupported()
         {
            // If there is any IdentityStore that supports named relationships...
            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported())
               {
                  return true;
               }
            }
            return defaultIdentityStore.getSupportedFeatures().isNamedRelationshipsSupported();
         }

         public boolean isRelationshipPropertiesSupported()
         {
             // If there is any IdentityStore that supports relationship properties...
            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               if (identityStore.getSupportedFeatures().isRelationshipPropertiesSupported())
               {
                  return true;
               }
            }
            return defaultIdentityStore.getSupportedFeatures().isRelationshipPropertiesSupported();
         }

         public boolean isSearchCriteriaTypeSupported(IdentityObjectType identityObjectType, IdentityObjectSearchCriteriaType storeSearchConstraint)
         {
            return resolveIdentityStore(identityObjectType).getSupportedFeatures().isSearchCriteriaTypeSupported(identityObjectType, storeSearchConstraint);
         }

         public Set<String> getSupportedIdentityObjectTypes()
         {
            Set<String> supportedIOTs = new HashSet<String>();

            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               supportedIOTs.addAll(identityStore.getSupportedFeatures().getSupportedIdentityObjectTypes());
            }
            supportedIOTs.addAll(defaultIdentityStore.getSupportedFeatures().getSupportedRelationshipTypes());

            return supportedIOTs;
         }

         public boolean isIdentityObjectTypeSupported(IdentityObjectType identityObjectType)
         {
            return resolveIdentityStore(identityObjectType).getSupportedFeatures().isIdentityObjectTypeSupported(identityObjectType);
         }

         public boolean isRelationshipTypeSupported(IdentityObjectType fromType, IdentityObjectType toType, IdentityObjectRelationshipType relationshipType) throws IdentityException
         {
            IdentityStore fromStore = resolveIdentityStore(fromType);

            IdentityStore toStore = resolveIdentityStore(toType);

            if (fromStore == toStore)
            {
               return fromStore.getSupportedFeatures().isRelationshipTypeSupported(fromType, toType, relationshipType);
            }
            else
            {
               return defaultIdentityStore.getSupportedFeatures().isRelationshipTypeSupported(fromType, toType, relationshipType);
            }

         }

         public Set<String> getSupportedRelationshipTypes()
         {
            Set<String> supportedRelTypes = new HashSet<String>();

            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               supportedRelTypes.addAll(identityStore.getSupportedFeatures().getSupportedRelationshipTypes());
            }
            supportedRelTypes.addAll(defaultIdentityStore.getSupportedFeatures().getSupportedRelationshipTypes());

            return supportedRelTypes;
         }

         public boolean isCredentialSupported(IdentityObjectType identityObjectType, IdentityObjectCredentialType credentialType)
         {
            return resolveIdentityStore(identityObjectType).getSupportedFeatures().isCredentialSupported(identityObjectType, credentialType);
         }

         public boolean isIdentityObjectAddRemoveSupported(IdentityObjectType objectType)
         {
            return resolveIdentityStore(objectType).getSupportedFeatures().isIdentityObjectAddRemoveSupported(objectType);
         }

         public boolean isRelationshipNameAddRemoveSupported()
         {
            // If there is any IdentityStore that supports named relationships...
            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               if (identityStore.getSupportedFeatures().isRelationshipNameAddRemoveSupported())
               {
                  return true;
               }
            }
            return defaultIdentityStore.getSupportedFeatures().isRelationshipNameAddRemoveSupported();

         }

         public boolean isRoleNameSearchCriteriaTypeSupported(IdentityObjectSearchCriteriaType constraint)
         {
            // If there is any IdentityStore that supports named relationships...
            for (IdentityStore identityStore : getIdentityStoreMappings().values())
            {
               if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported() &&
                  identityStore.getSupportedFeatures().isRoleNameSearchCriteriaTypeSupported(constraint))
               {
                  return true;
               }
            }
            return defaultIdentityStore.getSupportedFeatures().isNamedRelationshipsSupported() &&
               defaultIdentityStore.getSupportedFeatures().isRoleNameSearchCriteriaTypeSupported(constraint);
         }

      };

   }

   public void bootstrap(IdentityStoreConfigurationContext configurationContext) throws IdentityException
   {
      // Nothing
   }

   public IdentityStoreSession createIdentityStoreSession() throws IdentityException
   {
      Map<String, IdentityStoreSession> sessions = new HashMap<String, IdentityStoreSession>();

      for (IdentityStore identityStore : configuredIdentityStores)
      {
         sessions.put(identityStore.getId(), identityStore.createIdentityStoreSession());
      }

      for (AttributeStore attributeStore : configuredIdentityStores)
      {
         if (!sessions.containsKey(attributeStore.getId()))
         {
            sessions.put(attributeStore.getId(), attributeStore.createIdentityStoreSession());
         }
      }

      if (!sessions.containsKey(defaultAttributeStore.getId()))
      {
         sessions.put(defaultAttributeStore.getId(), defaultAttributeStore.createIdentityStoreSession());
      }

      if (!sessions.containsKey(defaultIdentityStore.getId()))
      {
         sessions.put(defaultIdentityStore.getId(), defaultIdentityStore.createIdentityStoreSession());
      }

      return new RepositoryIdentityStoreSessionImpl(sessions);
   }

   public IdentityStoreSession createIdentityStoreSession(
         Map<String, Object> sessionOptions) throws IdentityException
   {
      Map<String, IdentityStoreSession> sessions = new HashMap<String, IdentityStoreSession>();

      for (IdentityStore identityStore : configuredIdentityStores)
      {
         sessions.put(identityStore.getId(), identityStore.createIdentityStoreSession(sessionOptions));
      }

      for (AttributeStore attributeStore : configuredIdentityStores)
      {
         if (!sessions.containsKey(attributeStore.getId()))
         {
            sessions.put(attributeStore.getId(), attributeStore.createIdentityStoreSession(sessionOptions));
         }
      }

      if (!sessions.containsKey(defaultAttributeStore.getId()))
      {
         sessions.put(defaultAttributeStore.getId(), defaultAttributeStore.createIdentityStoreSession(sessionOptions));
      }

      if (!sessions.containsKey(defaultIdentityStore.getId()))
      {
         sessions.put(defaultIdentityStore.getId(), defaultIdentityStore.createIdentityStoreSession(sessionOptions));
      }

      return new RepositoryIdentityStoreSessionImpl(sessions);
   }

   public String getId()
   {
      return id;
   }

   public FeaturesMetaData getSupportedFeatures()
   {
      return featuresMetaData;
   }

   IdentityStore resolveIdentityStore(IdentityObject io) throws IdentityException
   {
      return resolveIdentityStore(io.getIdentityType());
   }

   /**
    * Should return mapped store which actually contain given IdentityObject.
    *
    * @param io
    * @return may return null
    * @throws IdentityException
    */
   IdentityStore resolveFirstIdentityStoreWithIO(IdentityObject io, IdentityStoreInvocationContext ic) throws IdentityException
   {
      List<IdentityStore> mappedStores = resolveIdentityStores(io.getIdentityType());

      for (IdentityStore mappedStore : mappedStores)
      {
         IdentityStoreInvocationContext mappedContext = resolveInvocationContext(mappedStore, ic);

         if (hasIdentityObject(mappedContext, mappedStore, io))
         {
            return mappedStore;
         }
      }

      return null;
   }

   IdentityStore resolveIdentityStore(IdentityObjectType iot)
   {

      IdentityStore ids = null;
      try
      {
         ids = getIdentityStore(iot);
      }
      catch (IdentityException e)
      {
         if (isAllowNotDefinedAttributes())
         {
            return defaultIdentityStore;
         }

         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }

         throw new IllegalStateException("Used IdentityObjectType not mapped. Consider using " + ALLOW_NOT_DEFINED_IDENTITY_OBJECT_TYPES_OPTION +
            " repository option switch: " + iot );
      }

      if (ids == null)
      {
         ids = defaultIdentityStore;
      }
      return ids;
   }

   List<IdentityStore> resolveIdentityStores(IdentityObjectType iot)
   {

      List<IdentityStore> ids = null;
      try
      {
         ids = getIdentityStores(iot);
      }
      catch (IdentityException e)
      {
         if (isAllowNotDefinedAttributes())
         {
            ids = new LinkedList<IdentityStore>();
            ids.add(defaultIdentityStore);
            return ids;
         }

         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }

         throw new IllegalStateException("Used IdentityObjectType not mapped. Consider using " + ALLOW_NOT_DEFINED_IDENTITY_OBJECT_TYPES_OPTION +
            " repository option switch: " + iot );
      }

      if (ids == null || ids.size() == 0)
      {
         ids = new LinkedList<IdentityStore>();
         ids.add(defaultIdentityStore);
      }
      return ids;
   }

   AttributeStore resolveAttributeStore(IdentityObjectType iot)
   {
      AttributeStore ads = null;
      try
      {
         ads = getAttributeStore(iot);
      }
      catch (IdentityException e)
      {
         if (isAllowNotDefinedAttributes())
         {
            return defaultAttributeStore;
         }

         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }

         throw new IllegalStateException("Used IdentityObjectType not mapped. Consider using " + ALLOW_NOT_DEFINED_IDENTITY_OBJECT_TYPES_OPTION +
         " repository option switch: " + iot );
      }

      if (ads == null)
      {
         ads = defaultAttributeStore;
      }
      return ads;
   }

   IdentityStoreInvocationContext resolveInvocationContext(IdentityStore targetStore, IdentityStoreInvocationContext invocationCtx)
   {
      return resolveInvocationContext(targetStore.getId(), invocationCtx);

   }

   IdentityStoreInvocationContext resolveInvocationContext(AttributeStore targetStore, IdentityStoreInvocationContext invocationCtx)
   {
      return resolveInvocationContext(targetStore.getId(), invocationCtx);

   }

   IdentityStoreInvocationContext resolveInvocationContext(String id, IdentityStoreInvocationContext invocationCtx)
   {
      RepositoryIdentityStoreSessionImpl repoSession = (RepositoryIdentityStoreSessionImpl)invocationCtx.getIdentityStoreSession();
      IdentityStoreSession targetSession = repoSession.getIdentityStoreSession(id);

      return new SimpleIdentityStoreInvocationContext(targetSession, invocationCtx.getRealmId(), String.valueOf(this.hashCode()));

   }

   public IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationCtx, String name, IdentityObjectType identityObjectType) throws IdentityException
   {
      IdentityStore targetStore = resolveIdentityStore(identityObjectType);
      IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);
      IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);

      // If object is present in default store then ignore
      IdentityObject mock = new SimpleIdentityObject(name, identityObjectType);
      if (targetStore != defaultIdentityStore && hasIdentityObject(defaultCtx, defaultIdentityStore, mock))
      {
         return mock;
      }

      if (isIdentityStoreReadOnly(targetStore))
      {
         targetStore = defaultIdentityStore;
         targetCtx = defaultCtx;

      }

      IdentityObject result = null;

      try
      {
         result = targetStore.createIdentityObject(targetCtx, name, identityObjectType);
      }
      catch (IdentityException e)
      {
         log.log(Level.SEVERE, "Failed to create IdentityObject: ", e);
      }

      return result;
   }

   public IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationCtx, String name, IdentityObjectType identityObjectType, Map<String, String[]> attributes) throws IdentityException
   {
      IdentityStore targetStore = resolveIdentityStore(identityObjectType);
      IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);
      IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);


      // If object is present in default store then ignore
      IdentityObject mock = new SimpleIdentityObject(name, identityObjectType);
      if (targetStore != defaultIdentityStore && hasIdentityObject(defaultCtx, defaultIdentityStore, mock))
      {
         return mock;
      }

      if (isIdentityStoreReadOnly(targetStore))
      {
         targetStore = defaultIdentityStore;
         targetCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);

      }

      IdentityObject result = null;

      try
      {
         result = targetStore.createIdentityObject(targetCtx, name, identityObjectType, attributes);
      }
      catch (IdentityException e)
      {
         log.log(Level.SEVERE, "Failed to create IdentityObject: ", e);
      }

      return result;
   }

   public void removeIdentityObject(IdentityStoreInvocationContext invocationCtx, IdentityObject identity) throws IdentityException
   {
      List<IdentityStore> targetStores = resolveIdentityStores(identity.getIdentityType());
      IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);

      for (IdentityStore targetStore : targetStores)
      {
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);

         if (isIdentityStoreReadOnly(targetStore))
         {
            targetStore = defaultIdentityStore;
            targetCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);

         }

         try
         {
            if (hasIdentityObject(targetCtx, targetStore, identity))
            {
               targetStore.removeIdentityObject(targetCtx, identity);
            }
         }
         catch (IdentityException e)
         {
            log.log(Level.SEVERE, "Failed to remove IdentityObject from target store: ", e);
         }
      }


      // Sync remove in default store
      if (!targetStores.contains(defaultIdentityStore) && hasIdentityObject(defaultCtx, defaultIdentityStore, identity))
      {

         try
         {
            defaultIdentityStore.removeIdentityObject(defaultCtx, identity);
         }
         catch (IdentityException e)
         {
            log.log(Level.SEVERE, "Failed to remove IdentityObject from default store: ", e);
         }
      }
   }

   public int getIdentityObjectsCount(IdentityStoreInvocationContext invocationCtx, IdentityObjectType identityType) throws IdentityException
   {
      List<IdentityStore> targetStores = resolveIdentityStores(identityType);

      //TODO: Result may be inaccurate - at least try to check if both stores don't match and return bigger count;

      int result = 0;

      if (!targetStores.contains(defaultIdentityStore))
      {
         targetStores.add(defaultIdentityStore);
      }

      for (IdentityStore targetStore : targetStores)
      {
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);
         try
         {
            result += targetStore.getIdentityObjectsCount(targetCtx, identityType);
         }
         catch (IdentityException e)
         {
            log.log(Level.SEVERE, "Failed to obtain IdentityObject count from store " + targetStore.getId() + " : ", e);
         }
      }
      

      return result;
   }

   public IdentityObject findIdentityObject(IdentityStoreInvocationContext invocationContext, String name, IdentityObjectType identityObjectType) throws IdentityException
   {
      List<IdentityStore> targetStores = resolveIdentityStores(identityObjectType);


      IdentityObject io = null;

      for (IdentityStore targetStore : targetStores)
      {
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationContext);

         try
         {
            io = targetStore.findIdentityObject(targetCtx, name, identityObjectType);
         }
         catch (IdentityException e)
         {
            log.log(Level.SEVERE, "Failed to find IdentityObject in target store: ", e);
         }

         if (io != null)
         {
            return io;
         }

      }

      
      try
      {
         io = defaultIdentityStore.
            findIdentityObject(resolveInvocationContext(defaultIdentityStore, invocationContext), name, identityObjectType);
      }
      catch (IdentityException e)
      {
         log.log(Level.SEVERE, "Failed to find IdentityObject in default store: ", e);
      }

      return io;
   }

   public IdentityObject findIdentityObject(IdentityStoreInvocationContext invocationContext, String id) throws IdentityException
   {
      //TODO: information about the store mapping should be encoded in id as now its like random guess and this kills performance ...

      for (IdentityStore identityStore : getIdentityStoreMappings().values())
      {
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(identityStore, invocationContext);

         IdentityObject io = identityStore.findIdentityObject(targetCtx, id);
         if (io != null)
         {
            return io;
         }
      }

      return defaultIdentityStore.findIdentityObject(invocationContext, id);
   }

   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                                        IdentityObjectType identityType,
                                                        IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      List<IdentityStore> targetStores = resolveIdentityStores(identityType);

      Collection<IdentityObject> results = new LinkedList<IdentityObject>();
      Collection<IdentityObject> defaultIOs = new LinkedList<IdentityObject>();



      if (targetStores.size() == 1 && targetStores.contains(defaultIdentityStore))
      {
         Collection<IdentityObject> resx = new LinkedList<IdentityObject>();

         try
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);
            resx = defaultIdentityStore.findIdentityObject(targetCtx, identityType, criteria);
         }
         catch (IdentityException e)
         {
            log.log(Level.SEVERE, "Exception occurred: ", e);
         }

         return resx;
      }
      else
      {


         IdentitySearchCriteriaImpl c = null;
         if (criteria != null)
         {
            c = new IdentitySearchCriteriaImpl(criteria);
            c.setPaged(false);
         }
         // Get results from default store with not paged criteria
         defaultIOs = defaultIdentityStore.
            findIdentityObject(resolveInvocationContext(defaultIdentityStore, invocationCtx), identityType, c);

         // if default store results are not present then apply criteria. Otherwise apply criteria without page
         // as result need to be merged
         if (defaultIOs.size() == 0 && targetStores.size() == 1)
         {
            Collection<IdentityObject> resx = new LinkedList<IdentityObject>();

            try
            {
               IdentityStore targetStore = targetStores.get(0);
               IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);
               resx =  targetStore.findIdentityObject(targetCtx, identityType, criteria);
            }
            catch (IdentityException e)
            {
               log.log(Level.SEVERE, "Exception occurred: ", e);
            }

            return resx;
         }
         else
         {

            for (IdentityStore targetStore : targetStores)
            {
               try
               {
                  IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationCtx);
                  results.addAll(targetStore.findIdentityObject(targetCtx, identityType, c));
               }
               catch (IdentityException e)
               {
                  log.log(Level.SEVERE, "Exception occurred: ", e);
               }
            }

         }

      }

      // Filter out duplicates
      HashSet<IdentityObject> merged = new HashSet<IdentityObject>();
      merged.addAll(results);
      merged.addAll(defaultIOs);

      //Apply criteria not applied at store level (sort/page)
      if (criteria != null)
      {

         LinkedList<IdentityObject> processed = new LinkedList<IdentityObject>(merged);

         //TODO: hardcoded - expects List
         if (criteria.isSorted())
         {
            sortByName(processed, criteria.isAscending());
         }

         results = processed;

         //TODO: hardcoded - expects List
         if (criteria.isPaged())
         {
            results = cutPageFromResults(processed, criteria);

         }
         
      }
      else
      {
         results = merged;
      }

      return results;


   }

   public int getIdentityObjectCount(IdentityStoreInvocationContext invocationCxt,
                                     IdentityObject identity,
                                     IdentityObjectRelationshipType relationshipType,
                                     boolean parent,
                                     IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      //TODO: merge vs sum strategy

      int count = 0;

      Collection<IdentityStore> mappedStores = getConfiguredIdentityStores();

      if (!mappedStores.contains(defaultIdentityStore))
      {
         mappedStores.add(defaultIdentityStore);
      }

      for (IdentityStore mappedStore : mappedStores)
      {
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(mappedStore, invocationCxt);

         count += mappedStore.getIdentityObjectCount(
            targetCtx,
            identity,
            relationshipType,
            parent,
            criteria
         );

      }

      return count;
   }

   public int getIdentityObjectCount(IdentityStoreInvocationContext ctx,
                                     IdentityObject identity,
                                     IdentityObjectRelationshipType relationshipType,
                                     Collection<IdentityObjectType> excludes,
                                     boolean parent,
                                     IdentityObjectSearchCriteria criteria) throws IdentityException
   {

      //TODO: merge vs sum strategy

      int count = 0;

      Collection<IdentityStore> mappedStores = getConfiguredIdentityStores();

      if (!mappedStores.contains(defaultIdentityStore))
      {
         mappedStores.add(defaultIdentityStore);
      }

      for (IdentityStore mappedStore : mappedStores)
      {

         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(mappedStore, ctx);

         count += mappedStore.getIdentityObjectCount(
            targetCtx,
            identity,
            relationshipType,
            excludes,
            parent,
            criteria
         );
      }
      return count;
   }


   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCxt,
                                                        IdentityObject identity,
                                                        IdentityObjectRelationshipType relationshipType,
                                                        boolean parent,
                                                       IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return findIdentityObject(invocationCxt, identity, relationshipType, null, parent, criteria);
   }


   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCxt,
                                                        IdentityObject identity,
                                                        IdentityObjectRelationshipType relationshipType,
                                                        Collection<IdentityObjectType> excludes,
                                                        boolean parent,
                                                       IdentityObjectSearchCriteria criteria) throws IdentityException
   {

      try
      {
         List<IdentityStore> mappedStores = resolveIdentityStores(identity.getIdentityType());

         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultIdentityStore, invocationCxt);


         // Maybe only default store match
         if (mappedStores.size() == 1 && mappedStores.contains(defaultIdentityStore))
         {
            return defaultIdentityStore.findIdentityObject(defaultCtx, identity, relationshipType, parent, criteria);
         }

         // For the merge no paging
         IdentitySearchCriteriaImpl c = null;

         if (criteria != null)
         {
            c = new IdentitySearchCriteriaImpl(criteria);
            c.setPaged(false);
         }

         Collection<IdentityObject> results = new LinkedList<IdentityObject>();

         // Filter out duplicates results
         HashSet<IdentityObject> merged = new HashSet<IdentityObject>();

         for (IdentityStore mappedStore : mappedStores)
         {
            IdentityStoreInvocationContext mappedCtx = resolveInvocationContext(mappedStore, invocationCxt);

            // If object is in the store but there is no rel type provided or it is not a role
            // So don't try to look for roles where they are not supported...
            if (hasIdentityObject(mappedCtx, mappedStore, identity)
               && (relationshipType == null
               || !RoleManagerImpl.ROLE.getName().equals(relationshipType.getName())
               || mappedStore.getSupportedFeatures().isNamedRelationshipsSupported())
               )
            {
               // If object present in identity store then don't apply page in criteria
               if (hasIdentityObject(defaultCtx, defaultIdentityStore, identity))
               {
                  results = mappedStore.findIdentityObject(mappedCtx, identity, relationshipType, parent, c);
                  // add with filter of duplicate
                  merged.addAll(results);
               }

               // Otherwise if there was only mapped store simply return results as it shouldn't be present
               // in default anyway...
               else if (mappedStores.size() == 1)
               {
                  return mappedStore.findIdentityObject(mappedCtx, identity, relationshipType, parent, criteria);
               }
            }

         }

         // So always check with default
         Collection<IdentityObject> objects = defaultIdentityStore.findIdentityObject(defaultCtx, identity, relationshipType, parent, c);

         // If default store contain related relationships merge and sort/page once more
         if (objects != null && objects.size() != 0)
         {
            merged.addAll(objects);

            // So as things were merged criteria need to be reapplied
            if (criteria != null)
            {

               LinkedList<IdentityObject> processed = new LinkedList<IdentityObject>(merged);



               //TODO: hardcoded - expects List
               if (criteria.isSorted())
               {
                  sortByName(processed, criteria.isAscending());
               }

               results = processed;

               //TODO: hardcoded - expects List
               if (criteria.isPaged())
               {
                  results = cutPageFromResults(processed, criteria);
               }


            }
            else
            {
               results = merged;
            }
         }

         return results;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }

         throw e;
      }

   }

   public IdentityObjectRelationship createRelationship(IdentityStoreInvocationContext invocationCxt, IdentityObject fromIdentity, IdentityObject toIdentity, IdentityObjectRelationshipType relationshipType, String relationshipName, boolean createNames) throws IdentityException
   {
      try
      {
         IdentityStore fromStore = resolveFirstIdentityStoreWithIO(fromIdentity, invocationCxt);

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(toIdentity, invocationCxt);

         IdentityStoreInvocationContext toTargetCtx =
            toStore != null ? resolveInvocationContext(toStore, invocationCxt): null;

         IdentityStoreInvocationContext defaultTargetCtx = resolveInvocationContext(defaultIdentityStore, invocationCxt);

         // Check if stores are not null so io exists in one of mappings.
         if ((fromStore != null && toStore != null) &&
             fromStore == toStore && !isIdentityStoreReadOnly(fromStore))
            {
            // If relationship is named and target store doesn't support named relationships it need to be put in default store anyway
            if (relationshipName == null ||
               (relationshipName != null && fromStore.getSupportedFeatures().isNamedRelationshipsSupported()))
            {
               return fromStore.createRelationship(toTargetCtx, fromIdentity, toIdentity, relationshipType, relationshipName, createNames);
            }
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, fromIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, fromIdentity.getName(),  fromIdentity.getIdentityType());
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, toIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, toIdentity.getName(),  toIdentity.getIdentityType());
         }

         return defaultIdentityStore.createRelationship(defaultTargetCtx, fromIdentity, toIdentity, relationshipType, relationshipName, createNames);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void removeRelationship(IdentityStoreInvocationContext invocationCxt, IdentityObject fromIdentity, IdentityObject toIdentity, IdentityObjectRelationshipType relationshipType, String relationshipName) throws IdentityException
   {
      try
      {
         IdentityStore fromStore = resolveFirstIdentityStoreWithIO(fromIdentity, invocationCxt);

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(toIdentity, invocationCxt);

         IdentityStoreInvocationContext toTargetCtx =
            toStore != null ? resolveInvocationContext(toStore, invocationCxt): null;

         IdentityStoreInvocationContext defaultTargetCtx = resolveInvocationContext(defaultIdentityStore, invocationCxt);

         // Check if stores are not null so io exists in one of mappings.
         if ((fromStore != null && toStore != null) &&
            fromStore == toStore && !isIdentityStoreReadOnly(fromStore))
         {
            if (relationshipName == null ||
               (relationshipName != null && fromStore.getSupportedFeatures().isNamedRelationshipsSupported()))
            {
               fromStore.removeRelationship(toTargetCtx, fromIdentity, toIdentity, relationshipType, relationshipName);
               return;
            }
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, fromIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, fromIdentity.getName(),  fromIdentity.getIdentityType());
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, toIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, toIdentity.getName(),  toIdentity.getIdentityType());
         }

         Set<IdentityObjectRelationship> rels =
                 defaultIdentityStore.resolveRelationships(defaultTargetCtx, fromIdentity, toIdentity, relationshipType);

         for (IdentityObjectRelationship rel : rels) {

            if (rel.getName() == null || rel.getName().equals(relationshipName))
            {
                defaultIdentityStore.
                        removeRelationship(defaultTargetCtx, fromIdentity, toIdentity, relationshipType, relationshipName);
            }
         }

      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }

         throw e;
      }
   }

   public void removeRelationships(IdentityStoreInvocationContext invocationCtx, IdentityObject identity1, IdentityObject identity2, boolean named) throws IdentityException
   {
      try
      {
         IdentityStore fromStore = resolveFirstIdentityStoreWithIO(identity1, invocationCtx);

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity2, invocationCtx);

         IdentityStoreInvocationContext toTargetCtx =
            toStore != null ? resolveInvocationContext(toStore, invocationCtx): null;

         IdentityStoreInvocationContext defaultTargetCtx = resolveInvocationContext(defaultIdentityStore, invocationCtx);


         // Check if stores are not null so io exists in one of mappings.
         if ((fromStore != null && toStore != null) && fromStore == toStore && !isIdentityStoreReadOnly(fromStore))
         {
            fromStore.removeRelationships(toTargetCtx, identity1, identity2, named);
            return;
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, identity1))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, identity1.getName(),  identity1.getIdentityType());
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, identity2))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, identity2.getName(),  identity2.getIdentityType());
         }

         defaultIdentityStore.removeRelationships(defaultTargetCtx, identity1, identity2, named);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Set<IdentityObjectRelationship> resolveRelationships(IdentityStoreInvocationContext invocationCxt,
                                                               IdentityObject fromIdentity,
                                                               IdentityObject toIdentity,
                                                               IdentityObjectRelationshipType relationshipType) throws IdentityException
   {

      try
      {
         IdentityStore fromStore = resolveFirstIdentityStoreWithIO(fromIdentity, invocationCxt);

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(toIdentity, invocationCxt);

         IdentityStoreInvocationContext toTargetCtx =
            toStore != null ? resolveInvocationContext(toStore, invocationCxt): null;

         IdentityStoreInvocationContext defaultTargetCtx = resolveInvocationContext(defaultIdentityStore, invocationCxt);

         // Check if stores are not null so io exists in one of mappings.
         if ((fromStore != null && toStore != null) &&
            fromStore == toStore &&
            (!RoleManagerImpl.ROLE.getName().equals(relationshipType.getName()) ||
             fromStore.getSupportedFeatures().isNamedRelationshipsSupported()))
         {
            return fromStore.resolveRelationships(toTargetCtx, fromIdentity, toIdentity, relationshipType);

         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, fromIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, fromIdentity.getName(),  fromIdentity.getIdentityType());
         }

         if (!hasIdentityObject(defaultTargetCtx, defaultIdentityStore, toIdentity))
         {
            defaultIdentityStore.createIdentityObject(defaultTargetCtx, toIdentity.getName(),  toIdentity.getIdentityType());
         }

         return defaultIdentityStore.resolveRelationships(defaultTargetCtx, fromIdentity, toIdentity, relationshipType);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public int getRelationshipsCount(IdentityStoreInvocationContext ctx,
                                    IdentityObject identity,
                                    IdentityObjectRelationshipType type,
                                    boolean parent,
                                    boolean named,
                                    String name,
                                    IdentityObjectSearchCriteria searchCriteria) throws IdentityException
   {

      //TODO: merge vs sum strategy

      int count = 0;

      Collection<IdentityStore> mappedStores = getConfiguredIdentityStores();

      for (IdentityStore mappedStore : mappedStores)
      {
         IdentityStoreInvocationContext storeCtx = resolveInvocationContext(mappedStore, ctx);


         if (hasIdentityObject(storeCtx, mappedStore, identity))
         {
            count += mappedStore.getRelationshipsCount(
               storeCtx,
               identity,
               type,
               parent,
               named,
               name,
               searchCriteria
            );
         }
      }

      return count;

   }

   public Set<IdentityObjectRelationship> resolveRelationships(IdentityStoreInvocationContext ctx,
                                                               IdentityObject identity,
                                                               IdentityObjectRelationshipType relationshipType,
                                                               boolean parent,
                                                               boolean named,
                                                               String name,
                                                               IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      try
      {
         Set<IdentityObjectRelationship> relationships = new HashSet<IdentityObjectRelationship>();

         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
//            if (relationshipType.getName() != null &&
//               !identityStore.getSupportedFeatures().getSupportedRelationshipTypes().contains(relationshipType.getName()))
//            {
//               continue;
//            }

            IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);

            //if ((!named || (named && identityStore.getSupportedFeatures().isNamedRelationshipsSupported()))
            //   && hasIdentityObject(storeCtx, identityStore, identity))
            if (hasIdentityObject(storeCtx, identityStore, identity))
            {
               relationships.addAll(identityStore.
                  resolveRelationships(storeCtx, identity, relationshipType, parent, named, name, criteria));
            }
         }

         return relationships;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public String createRelationshipName(IdentityStoreInvocationContext ctx, String name) throws IdentityException, OperationNotSupportedException
   {
      // For any IdentityStore that supports named relationships...
      try
      {
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported() && !isIdentityStoreReadOnly(identityStore))
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);
               identityStore.createRelationshipName(storeCtx, name);

            }
         }

         return name;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public String removeRelationshipName(IdentityStoreInvocationContext ctx, String name) throws IdentityException, OperationNotSupportedException
   {

      try
      {
         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported() && !isIdentityStoreReadOnly(identityStore))
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);
               identityStore.removeRelationshipName(storeCtx, name);

            }
         }

         return name;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Set<String> getRelationshipNames(IdentityStoreInvocationContext ctx, IdentityObjectSearchCriteria criteria) throws IdentityException, OperationNotSupportedException
   {
      try
      {
         Set<String> results = new HashSet<String>();

         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported())
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);
               results.addAll(identityStore.getRelationshipNames(storeCtx, criteria));

            }
         }

         return results;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Set<String> getRelationshipNames(IdentityStoreInvocationContext ctx, IdentityObject identity, IdentityObjectSearchCriteria criteria) throws IdentityException, OperationNotSupportedException
   {

      try
      {
         IdentityStore toStore = resolveIdentityStore(identity);
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, ctx);

         if (toStore.getSupportedFeatures().isNamedRelationshipsSupported())
         {
            return toStore.getRelationshipNames(targetCtx, identity, criteria);
         }
         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultIdentityStore, ctx);

         return defaultIdentityStore.getRelationshipNames(defaultCtx, identity, criteria);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Map<String, String> getRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name) throws IdentityException, OperationNotSupportedException
   {
      try
      {
         Map<String, String> results = new HashMap<String, String>();

         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported())
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);

               Map<String, String> props = identityStore.getRelationshipNameProperties(storeCtx, name);
               if (props != null && props.keySet().size() > 0)
               {
                  results.putAll(props);
               }

            }
         }

         return results;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void setRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name, Map<String, String> properties) throws IdentityException, OperationNotSupportedException
   {
      try
      {
         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported() && !isIdentityStoreReadOnly(identityStore))
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);

               identityStore.setRelationshipNameProperties(storeCtx, name, properties);

            }
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void removeRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name, Set<String> properties) throws IdentityException, OperationNotSupportedException
   {
      try
      {
         // For any IdentityStore that supports named relationships...
         for (IdentityStore identityStore : configuredIdentityStores)
         {
            if (identityStore.getSupportedFeatures().isNamedRelationshipsSupported() && !isIdentityStoreReadOnly(identityStore))
            {
               IdentityStoreInvocationContext storeCtx = resolveInvocationContext(identityStore, ctx);

               identityStore.removeRelationshipNameProperties(storeCtx, name, properties);

            }
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Map<String, String> getRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship) throws IdentityException, OperationNotSupportedException
   {

      try
      {
         IdentityStore fromStore = resolveIdentityStore(relationship.getFromIdentityObject());
         IdentityStore toStore = resolveIdentityStore(relationship.getToIdentityObject());

         if (fromStore == toStore && toStore.getSupportedFeatures().isNamedRelationshipsSupported())
         {
            return fromStore.getRelationshipProperties(resolveInvocationContext(fromStore, ctx), relationship);
         }

         return defaultIdentityStore.getRelationshipProperties(resolveInvocationContext(defaultIdentityStore, ctx), relationship);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void setRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship, Map<String, String> properties) throws IdentityException, OperationNotSupportedException
   {
      try
      {
         IdentityStore fromStore = resolveFirstIdentityStoreWithIO(relationship.getFromIdentityObject(), ctx);
         IdentityStore toStore = resolveFirstIdentityStoreWithIO(relationship.getToIdentityObject(), ctx);

         if (fromStore != null && toStore != null &&
            fromStore == toStore && toStore.getSupportedFeatures().isNamedRelationshipsSupported() &&
            !isIdentityStoreReadOnly(fromStore))
         {
            fromStore.setRelationshipProperties(resolveInvocationContext(fromStore, ctx), relationship, properties);
            return;
         }

         defaultIdentityStore.setRelationshipProperties(resolveInvocationContext(defaultIdentityStore, ctx), relationship, properties);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void removeRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship, Set<String> properties) throws IdentityException, OperationNotSupportedException
   {
      try
      {
          IdentityStore fromStore = resolveFirstIdentityStoreWithIO(relationship.getFromIdentityObject(), ctx);
         IdentityStore toStore = resolveFirstIdentityStoreWithIO(relationship.getToIdentityObject(), ctx);

         if (fromStore != null && toStore != null && fromStore == toStore &&
            toStore.getSupportedFeatures().isNamedRelationshipsSupported() && !isIdentityStoreReadOnly(fromStore))
         {
            fromStore.removeRelationshipProperties(resolveInvocationContext(fromStore, ctx), relationship, properties);
            return;
         }

         defaultIdentityStore.removeRelationshipProperties(resolveInvocationContext(defaultIdentityStore, ctx), relationship, properties);
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public boolean validateCredential(IdentityStoreInvocationContext ctx, IdentityObject identityObject, IdentityObjectCredential credential) throws IdentityException
   {
      try
      {
         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identityObject, ctx);

         if (toStore != null)
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, ctx);

            return toStore.validateCredential(targetCtx, identityObject, credential);
         }

         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(defaultIdentityStore, ctx);

         if (toStore != defaultIdentityStore && hasIdentityObject(targetCtx, defaultIdentityStore, identityObject))
         {
            return defaultIdentityStore.validateCredential(targetCtx, identityObject, credential);
         }

         return false;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void updateCredential(IdentityStoreInvocationContext ctx, IdentityObject identityObject, IdentityObjectCredential credential) throws IdentityException
   {
      try
      {
         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identityObject, ctx);

         if (toStore != null)
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, ctx);

            toStore.updateCredential(targetCtx, identityObject, credential);
            return;
         }

         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(defaultIdentityStore, ctx);

         if (toStore != defaultIdentityStore && hasIdentityObject(targetCtx, defaultIdentityStore, identityObject))
         {
            defaultIdentityStore.updateCredential(targetCtx, identityObject, credential);
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }


   public Set<String> getSupportedAttributeNames(IdentityStoreInvocationContext invocationContext, IdentityObjectType identityType) throws IdentityException
   {
      try
      {
         Set<String> results;


         // TODO: just get the first mapped store and use... should it merge supported attrs from different mapped stores?
         IdentityStore toStore = resolveIdentityStore(identityType);
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationContext);

         results = toStore.getSupportedAttributeNames(targetCtx, identityType);

         if (toStore != defaultAttributeStore)
         {
            IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationContext);

            results.addAll(defaultAttributeStore.getSupportedAttributeNames(defaultCtx, identityType));
         }

         return results;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Map<String, IdentityObjectAttributeMetaData> getAttributesMetaData(IdentityStoreInvocationContext invocationContext,
                                                                            IdentityObjectType identityObjectType)
   {

      try
      {

         // TODO: just get the first mapped store and use... should it merge supported attrs from different mapped stores?
         IdentityStore targetStore = resolveIdentityStore(identityObjectType);
         IdentityStoreInvocationContext targetCtx = resolveInvocationContext(targetStore, invocationContext);

         Map<String, IdentityObjectAttributeMetaData> mdMap = new HashMap<String, IdentityObjectAttributeMetaData>();
         mdMap.putAll(targetStore.getAttributesMetaData(targetCtx, identityObjectType));

         if (targetStore != defaultAttributeStore)
         {
            IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationContext);

            Map<String, IdentityObjectAttributeMetaData> defaultMDMap = defaultAttributeStore.getAttributesMetaData(defaultCtx, identityObjectType);


            // put all missing attribute MD from default store
            if (defaultMDMap != null)
            {
               for (Map.Entry<String, IdentityObjectAttributeMetaData> entry : defaultMDMap.entrySet())
               {
                  if (!mdMap.containsKey(entry.getKey()))
                  {
                     mdMap.put(entry.getKey(), entry.getValue());
                  }
               }
            }
         }

         return mdMap;
      }
      catch (Exception e)
      {
         log.log(Level.SEVERE, "Exception occurred: ", e);
      }
      return new HashMap<String, IdentityObjectAttributeMetaData>();
   }

   public IdentityObjectAttribute getAttribute(IdentityStoreInvocationContext invocationContext, IdentityObject identity, String name) throws IdentityException
   {
      try
      {
         IdentityObjectAttribute result = null;

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity, invocationContext);

         if (toStore != null)
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationContext);

            result = toStore.getAttribute(targetCtx, identity, name);
         }

         if (result == null && (toStore == null || toStore != defaultAttributeStore))
         {
            IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationContext);

            result = defaultAttributeStore.getAttribute(defaultCtx, identity, name);
         }

         return result;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public Map<String, IdentityObjectAttribute> getAttributes(IdentityStoreInvocationContext invocationContext, IdentityObject identity) throws IdentityException
   {
      try
      {
         Map<String, IdentityObjectAttribute> results = new HashMap<String, IdentityObjectAttribute>();

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity, invocationContext);


         if (toStore != null)
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationContext);
            results = toStore.getAttributes(targetCtx, identity);
         }

         if (toStore == null || toStore != defaultAttributeStore)
         {
            IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationContext);

            Map<String, IdentityObjectAttribute> defaultAttrs = defaultAttributeStore.getAttributes(defaultCtx, identity);

            // Add only those attributes which are missing - don't overwrite or merge existing values
            for (Map.Entry<String, IdentityObjectAttribute> entry : defaultAttrs.entrySet())
            {
               if (!results.keySet().contains(entry.getKey()))
               {
                  results.put(entry.getKey(), entry.getValue());
               }
            }
         }

         return results;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public void updateAttributes(IdentityStoreInvocationContext invocationCtx, IdentityObject identity, IdentityObjectAttribute[] attributes) throws IdentityException
   {
      try
      {
         ArrayList<IdentityObjectAttribute> filteredAttrs = new ArrayList<IdentityObjectAttribute>();
         ArrayList<IdentityObjectAttribute> leftAttrs = new ArrayList<IdentityObjectAttribute>();

         IdentityObjectAttribute[] attributesToAdd = null;

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity, invocationCtx);

         // Put supported attrs to the main store
         if (toStore != null && toStore != defaultAttributeStore
            && !isIdentityStoreReadOnly(toStore))
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationCtx);

            Set<String> supportedAttrs = toStore.getSupportedAttributeNames(targetCtx, identity.getIdentityType());

            // Filter out supported and not supported attributes
            for (IdentityObjectAttribute entry : attributes)
            {
               if (supportedAttrs.contains(entry.getName()))
               {
                  filteredAttrs.add(entry);
               }
               else
               {
                  leftAttrs.add(entry);
               }
            }

            toStore.updateAttributes(targetCtx, identity, filteredAttrs.toArray(new IdentityObjectAttribute[filteredAttrs.size()]));

            attributesToAdd = leftAttrs.toArray(new IdentityObjectAttribute[leftAttrs.size()]);

         }
         else
         {
            attributesToAdd = attributes;
         }

         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationCtx);

         if (isAllowNotDefinedAttributes())
         {
             if (!hasIdentityObject(defaultCtx, defaultIdentityStore, identity))
             {
                 defaultIdentityStore.createIdentityObject(defaultCtx, identity.getName(),  identity.getIdentityType());
             }
             defaultAttributeStore.updateAttributes(defaultCtx, identity, attributesToAdd);
         }
         else
         {
            Set<String> supportedAttrs = defaultAttributeStore.getSupportedAttributeNames(defaultCtx, identity.getIdentityType());
            for (IdentityObjectAttribute entry : leftAttrs)
            {
               if (!supportedAttrs.contains(entry.getName()))
               {
                  throw new IdentityException("Cannot update not defined attribute. Use '"
                     + ALLOW_NOT_DEFINED_ATTRIBUTES + "' option to pass such attributes to default IdentityStore anyway." +
                     "Attribute name: " + entry.getName());
               }
            }
            defaultAttributeStore.updateAttributes(defaultCtx, identity, attributesToAdd);
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }

   }

   public void addAttributes(IdentityStoreInvocationContext invocationCtx, IdentityObject identity, IdentityObjectAttribute[] attributes) throws IdentityException
   {

      try
      {
         ArrayList<IdentityObjectAttribute> filteredAttrs = new ArrayList<IdentityObjectAttribute>();
         ArrayList<IdentityObjectAttribute> leftAttrs = new ArrayList<IdentityObjectAttribute>();
         IdentityObjectAttribute[] attributesToAdd = null;

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity, invocationCtx);

         // Put supported attrs to the main store
         if (toStore != null &&
            toStore != defaultAttributeStore
            && !isIdentityStoreReadOnly(toStore))
         {
            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationCtx);


            Set<String> supportedAttrs = toStore.getSupportedAttributeNames(targetCtx, identity.getIdentityType());

            // Filter out supported and not supported attributes
            for (IdentityObjectAttribute entry : attributes)
            {
               if (supportedAttrs.contains(entry.getName()))
               {
                  filteredAttrs.add(entry);
               }
               else
               {
                  leftAttrs.add(entry);
               }
            }

            toStore.addAttributes(targetCtx, identity, filteredAttrs.toArray(new IdentityObjectAttribute[filteredAttrs.size()]));

            attributesToAdd = leftAttrs.toArray(new IdentityObjectAttribute[leftAttrs.size()]);


         }
         else
         {
            attributesToAdd = attributes;
         }

         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationCtx);

         if (isAllowNotDefinedAttributes())
         {
             if (!hasIdentityObject(defaultCtx, defaultIdentityStore, identity))
             {
                 defaultIdentityStore.createIdentityObject(defaultCtx, identity.getName(),  identity.getIdentityType());
             }

            defaultAttributeStore.addAttributes(defaultCtx, identity, attributesToAdd);
         }
         else
         {
            Set<String> supportedAttrs = defaultAttributeStore.getSupportedAttributeNames(defaultCtx, identity.getIdentityType());
            for (IdentityObjectAttribute entry : attributesToAdd)
            {
               // if we hit some unsupported attribute at this stage that we cannot store...
               if (!supportedAttrs.contains(entry.getName()))
               {
                  throw new IdentityException("Cannot add not defined attribute. Use '"
                     + ALLOW_NOT_DEFINED_ATTRIBUTES + "' option to pass such attributes to default IdentityStore anyway." +
                     "Attribute name: " + entry.getName());
               }

            }
            defaultAttributeStore.addAttributes(defaultCtx, identity, attributesToAdd);
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }

   }

   public void removeAttributes(IdentityStoreInvocationContext invocationCtx, IdentityObject identity, String[] attributes) throws IdentityException
   {
      try
      {
         List<String> filteredAttrs = new LinkedList<String>();
         List<String> leftAttrs = new LinkedList<String>();

         IdentityStore toStore = resolveFirstIdentityStoreWithIO(identity, invocationCtx);

         // Put supported attrs to the main store
         if (toStore != null &&
            toStore != defaultAttributeStore &&
            !isIdentityStoreReadOnly(toStore))
         {

            IdentityStoreInvocationContext targetCtx = resolveInvocationContext(toStore, invocationCtx);

            Set<String> supportedAttrs = toStore.getSupportedAttributeNames(targetCtx, identity.getIdentityType());

            // Filter out supported and not supported attributes
            for (String name : attributes)
            {
               if (supportedAttrs.contains(name))
               {
                  filteredAttrs.add(name);
               }
               else
               {
                  leftAttrs.add(name);
               }
            }

            toStore.removeAttributes(targetCtx, identity, filteredAttrs.toArray(new String[filteredAttrs.size()]));


         }
         else
         {
            leftAttrs = Arrays.asList(attributes);
         }

         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationCtx);

         if (isAllowNotDefinedAttributes())
         {
             if (!hasIdentityObject(defaultCtx, defaultIdentityStore, identity))
             {
                 defaultIdentityStore.createIdentityObject(defaultCtx, identity.getName(),  identity.getIdentityType());
             }
            defaultAttributeStore.removeAttributes(defaultCtx, identity, leftAttrs.toArray(new String[leftAttrs.size()]));
         }
         else
         {
            Set<String> supportedAttrs = defaultAttributeStore.getSupportedAttributeNames(defaultCtx, identity.getIdentityType());
            for (String name : leftAttrs)
            {
               if (!supportedAttrs.contains(name))
               {
                  throw new IdentityException("Cannot remove not defined attribute. Use '"
                     + ALLOW_NOT_DEFINED_ATTRIBUTES + "' option to pass such attributes to default IdentityStore anyway." +
                     "Attribute name: " + name);
               }
            }
            defaultAttributeStore.removeAttributes(defaultCtx, identity, leftAttrs.toArray(new String[leftAttrs.size()]));
         }
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   public IdentityObject findIdentityObjectByUniqueAttribute(IdentityStoreInvocationContext invocationCtx, IdentityObjectType identityObjectType, IdentityObjectAttribute attribute) throws IdentityException
   {
      try
      {

         Collection<IdentityStore> mappedStores = resolveIdentityStores(identityObjectType);

         IdentityObject result = null;

         for (IdentityStore mappedStore : mappedStores)
         {
            if (mappedStore != defaultAttributeStore)
            {

               IdentityStoreInvocationContext targetCtx = resolveInvocationContext(mappedStore, invocationCtx);

               Set<String> supportedAttrs = mappedStore.getSupportedAttributeNames(targetCtx, identityObjectType);

               if (supportedAttrs.contains(attribute.getName()))
               {
                  result = mappedStore.findIdentityObjectByUniqueAttribute(targetCtx, identityObjectType, attribute);
               }

               // First with any result win
               if (result != null)
               {
                  return result;
               }
            }
         }



         // And if we are still here just go with default
         IdentityStoreInvocationContext defaultCtx = resolveInvocationContext(defaultAttributeStore, invocationCtx);

         if (isAllowNotDefinedAttributes())
         {
            return defaultAttributeStore.findIdentityObjectByUniqueAttribute(defaultCtx, identityObjectType, attribute);
         }
         else
         {
            Set<String> supportedAttrs = defaultAttributeStore.getSupportedAttributeNames(defaultCtx, identityObjectType);
            if (supportedAttrs.contains(attribute.getName()))
            {
               return defaultAttributeStore.findIdentityObjectByUniqueAttribute(defaultCtx, identityObjectType, attribute);
            }
         }

         return null;
      }
      catch (IdentityException e)
      {
         if (log.isLoggable(Level.FINER))
         {
            log.log(Level.FINER, "Exception occurred: ", e);
         }
         throw e;
      }
   }

   private void sortByName(List<IdentityObject> objects, final boolean ascending)
   {
      Collections.sort(objects, new Comparator<IdentityObject>(){
         public int compare(IdentityObject o1, IdentityObject o2)
         {
            if (ascending)
            {
               return o1.getName().compareTo(o2.getName());
            }
            else
            {
               return o2.getName().compareTo(o1.getName());
            }
         }
      });
   }

   //TODO: other way
   private List<IdentityObject> cutPageFromResults(List<IdentityObject> objects, IdentityObjectSearchCriteria criteria)
   {

      List<IdentityObject> results = new LinkedList<IdentityObject>();

      if (criteria.getMaxResults() == 0)
      {
         for (int i = criteria.getFirstResult(); i < objects.size(); i++)
         {
            if (i < objects.size())
            {
               results.add(objects.get(i));
            }
         }
      }
      else
      {
         for (int i = criteria.getFirstResult(); i < criteria.getFirstResult() + criteria.getMaxResults(); i++)
         {
            if (i < objects.size())
            {
               results.add(objects.get(i));
            }
         }
      }
      return results;
   }

   public boolean isAllowNotDefinedAttributes()
   {
      return allowNotDefinedAttributes;
   }

   public boolean isIdentityStoreReadOnly(IdentityStore store)
   {
      List<IdentityStoreMappingMetaData> mappingMDs =
         configurationContext.getRepositoryConfigurationMetaData().getIdentityStoreToIdentityObjectTypeMappings();

      for (IdentityStoreMappingMetaData mappingMD : mappingMDs)
      {
         if (mappingMD.getIdentityStoreId().equals(store.getId()))
         {
            String value = mappingMD.getOptionSingleValue(OPTION_READ_ONLY);
            if (value != null && value.equalsIgnoreCase("true"))
            {
               return true;
            }
         }
      }

      return false;
   }
}
