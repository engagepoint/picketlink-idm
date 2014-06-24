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

import org.picketlink.idm.spi.store.*;
import org.picketlink.idm.spi.configuration.metadata.IdentityObjectAttributeMetaData;
import org.picketlink.idm.spi.configuration.IdentityRepositoryConfigurationContext;
import org.picketlink.idm.spi.configuration.IdentityStoreConfigurationContext;
import org.picketlink.idm.spi.model.IdentityObject;
import org.picketlink.idm.spi.model.IdentityObjectType;
import org.picketlink.idm.spi.model.IdentityObjectRelationshipType;
import org.picketlink.idm.spi.model.IdentityObjectRelationship;
import org.picketlink.idm.spi.model.IdentityObjectCredential;
import org.picketlink.idm.spi.model.IdentityObjectAttribute;
import org.picketlink.idm.spi.exception.OperationNotSupportedException;
import org.picketlink.idm.spi.search.IdentityObjectSearchCriteria;
import org.picketlink.idm.common.exception.IdentityException;
import org.picketlink.idm.impl.store.SimpleIdentityStoreInvocationContext;

import java.util.*;

/**
 * Simply wraps IdentityStore and AttributeStore and delegates all the calls
 *
 * @author <a href="mailto:boleslaw.dawidowicz at redhat.com">Boleslaw Dawidowicz</a>
 * @version : 0.1 $
 */
public class WrapperIdentityStoreRepository extends AbstractIdentityStoreRepository implements IdentityStoreExt
{

   private final String id;


   public WrapperIdentityStoreRepository(String id)
   {
      this.id = id;
   }

   @Override
   public void bootstrap(IdentityRepositoryConfigurationContext configurationContext,
                         Map<String, IdentityStore> bootstrappedIdentityStores,
                         Map<String, AttributeStore> bootstrappedAttributeStores) throws IdentityException
   {
      super.bootstrap(configurationContext, bootstrappedIdentityStores, bootstrappedAttributeStores);
   }

   public void bootstrap(IdentityStoreConfigurationContext configurationContext) throws IdentityException
   {
      //Nothing
   }

   public IdentityStoreSession createIdentityStoreSession() throws IdentityException
   {
      Map<String, IdentityStoreSession> sessions = new HashMap<String, IdentityStoreSession>();

      sessions.put(defaultAttributeStore.getId(), defaultAttributeStore.createIdentityStoreSession());

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

      sessions.put(defaultAttributeStore.getId(), defaultAttributeStore.createIdentityStoreSession(sessionOptions));

      if (!sessions.containsKey(defaultIdentityStore.getId()))
      {
         sessions.put(defaultIdentityStore.getId(), defaultIdentityStore.createIdentityStoreSession(sessionOptions));
      }

      return new RepositoryIdentityStoreSessionImpl(sessions);
   }

   IdentityStoreInvocationContext resolveIdentityStoreInvocationContext(IdentityStoreInvocationContext invocationCtx)
   {
      return resolveInvocationContext(defaultIdentityStore.getId(), invocationCtx);

   }

   IdentityStoreInvocationContext resolveAttributeStoreInvocationContext(IdentityStoreInvocationContext invocationCtx)
   {
      return resolveInvocationContext(defaultAttributeStore.getId(), invocationCtx);

   }

   IdentityStoreInvocationContext resolveInvocationContext(String id, IdentityStoreInvocationContext invocationCtx)
   {
      RepositoryIdentityStoreSessionImpl repoSession = (RepositoryIdentityStoreSessionImpl)invocationCtx.getIdentityStoreSession();
      IdentityStoreSession targetSession = repoSession.getIdentityStoreSession(id);

      return new SimpleIdentityStoreInvocationContext(targetSession, invocationCtx.getRealmId(), String.valueOf(this.hashCode()));

   }

   public String getId()
   {
      return id;
   }

   public FeaturesMetaData getSupportedFeatures()
   {
      return defaultIdentityStore.getSupportedFeatures();
   }

   public IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                              String name,
                                              IdentityObjectType identityObjectType) throws IdentityException
   {
      return defaultIdentityStore.createIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), name, identityObjectType);
   }

   public IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                              String name,
                                              IdentityObjectType identityObjectType,
                                              Map<String, String[]> attributes) throws IdentityException
   {
      return defaultIdentityStore.createIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), name, identityObjectType, attributes);
   }

   public void removeIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                    IdentityObject identity) throws IdentityException
   {
      defaultIdentityStore.removeIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), identity);
   }

   public int getIdentityObjectsCount(IdentityStoreInvocationContext invocationCtx,
                                      IdentityObjectType identityType) throws IdentityException
   {
      return defaultIdentityStore.getIdentityObjectsCount(resolveIdentityStoreInvocationContext(invocationCtx), identityType);
   }

   public IdentityObject findIdentityObject(IdentityStoreInvocationContext invocationContext,
                                            String name,
                                            IdentityObjectType identityObjectType) throws IdentityException
   {
      return defaultIdentityStore.findIdentityObject(resolveIdentityStoreInvocationContext(invocationContext), name, identityObjectType);
   }

   public IdentityObject findIdentityObject(IdentityStoreInvocationContext invocationContext,
                                            String id) throws IdentityException
   {
      return defaultIdentityStore.findIdentityObject(resolveIdentityStoreInvocationContext(invocationContext), id);
   }

   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                                        IdentityObjectType identityType,
                                                        IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.findIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), identityType, criteria);
   }

   public int getIdentityObjectCount(IdentityStoreInvocationContext invocationCtx,
                                     IdentityObject identity,
                                     IdentityObjectRelationshipType relationshipType,
                                     boolean parent,
                                     IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.getIdentityObjectCount(
         resolveIdentityStoreInvocationContext(invocationCtx),
         identity,
         relationshipType,
         parent,
         criteria
      );
   }

   public int getIdentityObjectCount(IdentityStoreInvocationContext ctx,
                                     IdentityObject identity,
                                     IdentityObjectRelationshipType relationshipType,
                                     Collection<IdentityObjectType> excludes,
                                     boolean parent,
                                     IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.getIdentityObjectCount(
         resolveIdentityStoreInvocationContext(ctx),
         identity,
         relationshipType,
         excludes,
         parent,
         criteria
      );
   }

   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                                        IdentityObject identity,
                                                        IdentityObjectRelationshipType relationshipType,
                                                        Collection<IdentityObjectType> excludes,
                                                        boolean parent,
                                                        IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.findIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), identity, relationshipType, excludes, parent, criteria);
   }

   public Collection<IdentityObject> findIdentityObject(IdentityStoreInvocationContext invocationCtx,
                                                        IdentityObject identity,
                                                        IdentityObjectRelationshipType relationshipType,
                                                        boolean parent,
                                                        IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.findIdentityObject(resolveIdentityStoreInvocationContext(invocationCtx), identity, relationshipType, parent, criteria);
   }

   public IdentityObjectRelationship createRelationship(IdentityStoreInvocationContext invocationCxt,
                                                        IdentityObject fromIdentity,
                                                        IdentityObject toIdentity,
                                                        IdentityObjectRelationshipType relationshipType,
                                                        String relationshipName,
                                                        boolean createNames) throws IdentityException
   {
      return defaultIdentityStore.createRelationship(resolveIdentityStoreInvocationContext(invocationCxt),
         fromIdentity, toIdentity, relationshipType, relationshipName, createNames);
   }

   public void removeRelationship(IdentityStoreInvocationContext invocationCxt,
                                  IdentityObject fromIdentity,
                                  IdentityObject toIdentity,
                                  IdentityObjectRelationshipType relationshipType,
                                  String relationshipName) throws IdentityException
   {
      defaultIdentityStore.removeRelationship(resolveIdentityStoreInvocationContext(invocationCxt), fromIdentity, toIdentity, relationshipType, relationshipName);
   }

   public void removeRelationships(IdentityStoreInvocationContext invocationCtx,
                                   IdentityObject identity1,
                                   IdentityObject identity2,
                                   boolean named) throws IdentityException
   {
      defaultIdentityStore.removeRelationships(resolveIdentityStoreInvocationContext(invocationCtx), identity1, identity2, named);
   }

   public Set<IdentityObjectRelationship> resolveRelationships(IdentityStoreInvocationContext invocationCxt,
                                                               IdentityObject fromIdentity,
                                                               IdentityObject toIdentity,
                                                               IdentityObjectRelationshipType relationshipType) throws IdentityException
   {
      return defaultIdentityStore.resolveRelationships(resolveIdentityStoreInvocationContext(invocationCxt), fromIdentity, toIdentity, relationshipType);
   }



   public int getRelationshipsCount(IdentityStoreInvocationContext ctx,
                                    IdentityObject identity,
                                    IdentityObjectRelationshipType type,
                                    boolean parent,
                                    boolean named,
                                    String name,
                                    IdentityObjectSearchCriteria searchCriteria) throws IdentityException
   {
      return defaultIdentityStore.getRelationshipsCount(
         resolveIdentityStoreInvocationContext(ctx),
         identity,
         type,
         parent,
         named,
         name,
         searchCriteria
      );
   }

   public Set<IdentityObjectRelationship> resolveRelationships(IdentityStoreInvocationContext invocationCtx,
                                                               IdentityObject identity,
                                                               IdentityObjectRelationshipType relationshipType,
                                                               boolean parent,
                                                               boolean named,
                                                               String name,
                                                               IdentityObjectSearchCriteria criteria) throws IdentityException
   {
      return defaultIdentityStore.resolveRelationships(resolveIdentityStoreInvocationContext(invocationCtx),
         identity, relationshipType, parent, named, name, criteria);
   }

   public String createRelationshipName(IdentityStoreInvocationContext ctx,
                                        String name) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.createRelationshipName(resolveIdentityStoreInvocationContext(ctx), name);
   }

   public String removeRelationshipName(IdentityStoreInvocationContext ctx,
                                        String name) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.removeRelationshipName(resolveIdentityStoreInvocationContext(ctx), name);
   }

   public Set<String> getRelationshipNames(IdentityStoreInvocationContext ctx,
                                           IdentityObjectSearchCriteria criteria) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.getRelationshipNames(resolveIdentityStoreInvocationContext(ctx), criteria);
   }

   public Set<String> getRelationshipNames(IdentityStoreInvocationContext ctx,
                                           IdentityObject identity,
                                           IdentityObjectSearchCriteria criteria) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.getRelationshipNames(resolveIdentityStoreInvocationContext(ctx), identity, criteria);
   }

   public Map<String, String> getRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.getRelationshipNameProperties(resolveAttributeStoreInvocationContext(ctx), name);
   }

   public void setRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name, Map<String, String> properties) throws IdentityException, OperationNotSupportedException
   {
      defaultIdentityStore.setRelationshipNameProperties(resolveIdentityStoreInvocationContext(ctx), name, properties);

   }

   public void removeRelationshipNameProperties(IdentityStoreInvocationContext ctx, String name, Set<String> properties) throws IdentityException, OperationNotSupportedException
   {
      defaultIdentityStore.removeRelationshipNameProperties(resolveIdentityStoreInvocationContext(ctx), name, properties);
   }

   public Map<String, String> getRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship) throws IdentityException, OperationNotSupportedException
   {
      return defaultIdentityStore.getRelationshipProperties(resolveIdentityStoreInvocationContext(ctx), relationship);
   }

   public void setRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship, Map<String, String> properties) throws IdentityException, OperationNotSupportedException
   {
      defaultIdentityStore.setRelationshipProperties(resolveIdentityStoreInvocationContext(ctx), relationship, properties);
   }

   public void removeRelationshipProperties(IdentityStoreInvocationContext ctx, IdentityObjectRelationship relationship, Set<String> properties) throws IdentityException, OperationNotSupportedException
   {
      defaultIdentityStore.removeRelationshipProperties(resolveIdentityStoreInvocationContext(ctx), relationship, properties);
   }

   public boolean validateCredential(IdentityStoreInvocationContext ctx,
                                     IdentityObject identityObject,
                                     IdentityObjectCredential credential) throws IdentityException
   {
      return defaultIdentityStore.validateCredential(resolveIdentityStoreInvocationContext(ctx), identityObject, credential);
   }

   public void updateCredential(IdentityStoreInvocationContext ctx,
                                IdentityObject identityObject,
                                IdentityObjectCredential credential) throws IdentityException
   {
      defaultIdentityStore.updateCredential(resolveIdentityStoreInvocationContext(ctx), identityObject, credential);
   }

   public Set<String> getSupportedAttributeNames(IdentityStoreInvocationContext invocationContext,
                                                 IdentityObjectType identityType) throws IdentityException
   {
      return defaultAttributeStore.getSupportedAttributeNames(resolveAttributeStoreInvocationContext(invocationContext), identityType);
   }

   public Map<String, IdentityObjectAttributeMetaData> getAttributesMetaData(IdentityStoreInvocationContext invocationContext,
                                                                             IdentityObjectType identityType)
   {
      return defaultAttributeStore.getAttributesMetaData(resolveAttributeStoreInvocationContext(invocationContext), identityType);
   }

   public Map<String, IdentityObjectAttribute> getAttributes(IdentityStoreInvocationContext invocationContext,
                                                             IdentityObject identity) throws IdentityException
   {
      return defaultAttributeStore.getAttributes(resolveAttributeStoreInvocationContext(invocationContext), identity);
   }

   public IdentityObjectAttribute getAttribute(IdentityStoreInvocationContext invocationContext,
                                               IdentityObject identity,
                                               String name) throws IdentityException
   {
      return defaultAttributeStore.getAttribute(resolveAttributeStoreInvocationContext(invocationContext), identity, name);
   }

   public void updateAttributes(IdentityStoreInvocationContext invocationCtx,
                                IdentityObject identity,
                                IdentityObjectAttribute[] attributes) throws IdentityException
   {
      defaultAttributeStore.updateAttributes(resolveAttributeStoreInvocationContext(invocationCtx), identity, attributes);
   }

   public void addAttributes(IdentityStoreInvocationContext invocationCtx,
                             IdentityObject identity,
                             IdentityObjectAttribute[] attributes) throws IdentityException
   {
      defaultAttributeStore.addAttributes(resolveAttributeStoreInvocationContext(invocationCtx), identity, attributes);
   }

   public void removeAttributes(IdentityStoreInvocationContext invocationCtx,
                                IdentityObject identity,
                                String[] attributeNames) throws IdentityException
   {
      defaultAttributeStore.removeAttributes(resolveAttributeStoreInvocationContext(invocationCtx), identity, attributeNames);
   }

   public IdentityObject findIdentityObjectByUniqueAttribute(IdentityStoreInvocationContext invocationCtx, IdentityObjectType identityObjectType, IdentityObjectAttribute attribute) throws IdentityException
   {
      return defaultAttributeStore.findIdentityObjectByUniqueAttribute(resolveAttributeStoreInvocationContext(invocationCtx), identityObjectType, attribute);
   }

    /**
     * @param identityName
     * @param oldPassword
     * @param newPassword
     * @return
     */
    public void changePassword(String identityName, String oldPassword, String newPassword) throws IdentityException {
        try{
            ((IdentityStoreExt)defaultIdentityStore).changePassword(identityName, oldPassword, newPassword);
        }catch(ClassCastException e){
            throw new IdentityException("Method changePassword(identityName, oldPassword, newPassword) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }
    }

    /**
     * @param identityName
     * @param challengePairs
     * @param newPassword
     * @return
     */
    public void forgotPassword(String identityName, Map<String, String> challengePairs, String newPassword) throws IdentityException {
        try{
            ((IdentityStoreExt)defaultIdentityStore).forgotPassword(identityName, challengePairs, newPassword);
        }catch(ClassCastException e){
            throw new IdentityException("Method forgotPassword(String identityName, Map<String, String> challengePairs, String newPassword) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }
    }

    /**
     * @param invocationContext
     * @param identityName
     * @param attributes
     * @return
     * @throws org.picketlink.idm.common.exception.IdentityException
     */
    public IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationContext, String identityName, Map<String, List<String>> attributes) throws IdentityException {
        IdentityObject identityObject;
        try{
        identityObject = ((IdentityStoreExt)defaultIdentityStore).createIdentityObject(invocationContext, identityName, attributes);
        }catch (ClassCastException e){
            throw new IdentityException("Method createUser(String identityName, Map<String, String> attributes) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }
        return identityObject;
    }

    /**
     * @param identityName
     * @return
     * @throws org.picketlink.idm.common.exception.IdentityException
     */
    public List<String> getChallengeQuestions(String identityName) throws IdentityException {
        List<String> challengeQuestions;
        try{
            challengeQuestions = ((IdentityStoreExt)defaultIdentityStore).getChallengeQuestions(identityName);
        }catch (ClassCastException e){
            throw new IdentityException("Method getChallengeQuestions(String identityName) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }
        return challengeQuestions;
    }

    /**
     *
     * @param identityName
     * @param attributes
     * @throws IdentityException
     */
    public void updateIdentityObjectAttributes(String identityName, Map<String, List<String>> attributes) throws IdentityException {
        try{
            ((IdentityStoreExt)defaultIdentityStore).updateIdentityObjectAttributes(identityName, attributes);
        }catch(ClassCastException e){
            throw new IdentityException("Method updateUserAttributes(String identityName, Map<String, String> attributes) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }
    }

	/**
	 *
	 * @param userId
	 * @return role list
	 * @throws IdentityException
	 */
	public List<String> getPersonRoles(String userId) throws IdentityException {
		List<String> roleList = new ArrayList<String>();
		try {
			roleList = ((IdentityStoreExt)defaultIdentityStore).getPersonRoles(userId);
		}catch(ClassCastException e){
			throw new IdentityException("Method getPersonRoles(String userId) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
		}
		return roleList;
	}

    public void deleteIdentityObject(String identityName) throws IdentityException {

        try{
           ((IdentityStoreExt)defaultIdentityStore).deleteIdentityObject(identityName);
        }catch (ClassCastException e){
            throw new IdentityException("Method createUser(String identityName, Map<String, String> attributes) can be used only with stores implementations  witch implement both of IdentityStore and IdentityStoreExt");
        }

    }
}
