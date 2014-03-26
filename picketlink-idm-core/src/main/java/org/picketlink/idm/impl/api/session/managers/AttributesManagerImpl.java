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

package org.picketlink.idm.impl.api.session.managers;

import org.picketlink.idm.api.AttributesManager;
import org.picketlink.idm.api.IdentityType;
import org.picketlink.idm.api.User;
import org.picketlink.idm.api.AttributeDescription;
import org.picketlink.idm.api.CredentialType;
import org.picketlink.idm.api.Credential;
import org.picketlink.idm.api.Attribute;
import org.picketlink.idm.api.Group;
import org.picketlink.idm.common.exception.IdentityException;
import org.picketlink.idm.spi.model.IdentityObjectCredential;
import org.picketlink.idm.spi.model.IdentityObjectAttribute;
import org.picketlink.idm.spi.model.IdentityObject;
import org.picketlink.idm.spi.configuration.metadata.IdentityObjectAttributeMetaData;
import org.picketlink.idm.impl.api.attribute.IdentityObjectAttributeMetaDataImpl;
import org.picketlink.idm.impl.api.SimpleAttribute;
import org.picketlink.idm.impl.api.PasswordCredential;
import org.picketlink.idm.impl.api.SimpleCredentialType;
import org.picketlink.idm.impl.api.session.IdentitySessionImpl;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:boleslaw.dawidowicz at redhat.com">Boleslaw Dawidowicz</a>
 * @version : 0.1 $
 */
public class AttributesManagerImpl extends AbstractManager implements AttributesManager, Serializable
{

   private static Logger log = Logger.getLogger(AttributesManagerImpl.class.getName());

   private static final long serialVersionUID = 1285532201632609092L;

   public AttributesManagerImpl(IdentitySessionImpl session)
   {
      super(session);
   }

   public AttributeDescription getAttributeDescription(IdentityType identityType, String name)
   {

      checkNotNullArgument(identityType, "IdentityType");
      checkNotNullArgument(name, "Attribute name");

      Map<String, IdentityObjectAttributeMetaData> mdMap =
         getRepository().getAttributesMetaData(getInvocationContext(), createIdentityObject(identityType).getIdentityType());

      if (mdMap != null && mdMap.containsKey(name))
      {
         IdentityObjectAttributeMetaData attributeMD = mdMap.get(name);
         if (attributeMD instanceof AttributeDescription)
         {
            return (AttributeDescription)attributeMD;
         }
         else
         {
            return new IdentityObjectAttributeMetaDataImpl(attributeMD);
         }
      }
      
      return null;

   }

   public AttributeDescription getAttributeDescription(String id, String attributeName)
   {
      checkNotNullArgument(id, "IdentityType Id");
      checkNotNullArgument(attributeName, "Attribute name");

      IdentityType identityType = createIdentityTypeFromId(id);

      return getAttributeDescription(identityType, attributeName);
      
   }

   public Map<String, AttributeDescription> getSupportedAttributesDescriptions(IdentityType identityType)
   {

      checkNotNullArgument(identityType, "IdentityType");

      Map<String, IdentityObjectAttributeMetaData> mdMap =
         getRepository().getAttributesMetaData(getInvocationContext(), createIdentityObject(identityType).getIdentityType());

      Map<String, AttributeDescription> descriptionMap = new HashMap<String, AttributeDescription>();

      if (mdMap != null)
      {
         for (IdentityObjectAttributeMetaData attributeMD : mdMap.values())
         {
            if (attributeMD instanceof AttributeDescription)
            {
               descriptionMap.put(attributeMD.getName(), (AttributeDescription)attributeMD);
            }
            else
            {
               descriptionMap.put(attributeMD.getName(), new IdentityObjectAttributeMetaDataImpl(attributeMD));
            }
         }
      }

      return descriptionMap;
   }

   public Map<String, AttributeDescription> getSupportedAttributesDescriptions(String id)
   {
      checkNotNullArgument(id, "Id (Group) or name (User)");

      IdentityType identityType = createIdentityTypeFromId(id);

      return getSupportedAttributesDescriptions(identityType);
   }

   public Set<String> getSupportedAttributeNames(IdentityType identityType) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");

         return getRepository().getSupportedAttributeNames(getInvocationContext(), createIdentityObject(identityType).getIdentityType());
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

   public Set<String> getSupportedAttributeNames(String id) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");

         IdentityType identityType = createIdentityTypeFromId(id);

         return getSupportedAttributeNames(identityType);
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

   public Map<String, Attribute> getAttributes(IdentityType identityType) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");

         if (cache != null)
         {
            Map<String, Attribute> attributes = cache.getAttributes(cacheNS, identityType.getKey());
            if (attributes != null)
            {
               return attributes;
            }
         }

         Map<String, IdentityObjectAttribute> map = getRepository().getAttributes(getInvocationContext(), createIdentityObject(identityType));

         Map<String, Attribute> newMap = new HashMap<String, Attribute>();

         for (Map.Entry<String, IdentityObjectAttribute> entry : map.entrySet())
         {
            newMap.put(entry.getKey(), convertAttribute(entry.getValue()));
         }

         if (cache != null)
         {
            cache.putAttributes(cacheNS, identityType.getKey(), newMap);
         }
         return newMap;
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

   public Map<String, Attribute> getAttributes(String id) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");


         IdentityType identityType = createIdentityTypeFromId(id);

         return getAttributes(identityType);
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

   public void updateAttributes(IdentityType identity, Attribute[] attributes) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identity, "IdentityType");
         checkNotNullArgument(attributes, "Attributes");

         preAttributesUpdate(identity, attributes);

         getRepository().updateAttributes(getInvocationContext(), createIdentityObject(identity), convertAttributes(attributes));

         if (cache != null)
         {
            // Grab the new profile and persist in cache
            cache.invalidateAttributes(cacheNS, identity.getKey());
            this.getAttributes(identity);
         }

         postAttributesUpdate(identity, attributes);
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

   public void updateAttributes(String id, Attribute[] attributes) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributes, "Attributes");

         IdentityType identityType = createIdentityTypeFromId(id);

         updateAttributes(identityType, attributes);
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

   public Attribute getAttribute(IdentityType identityType, String attributeName) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");
         checkNotNullArgument(attributeName, "Attribute name");

         return getAttributes(identityType).get(attributeName);
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

   public Attribute getAttribute(String id, String attributeName) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributeName, "Attribute name");

         IdentityType identityType = createIdentityTypeFromId(id);

         return getAttribute(identityType, attributeName);
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

   public void addAttribute(IdentityType identityType, String attributeName, Object[] values) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(values, "Attribute values");

         Attribute[] attrs = new Attribute[]{new SimpleAttribute(attributeName, values)};


         addAttributes(identityType, attrs);
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

   public void addAttributes(String id, Attribute[] attributes) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributes, "Attributes");

         IdentityType identityType = createIdentityTypeFromId(id);

         addAttributes(identityType, attributes);
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

   public void addAttribute(IdentityType identityType, String attributeName, Object value) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(value, "Attribute value");

         Attribute[] attrs = new Attribute[]{new SimpleAttribute(attributeName, value)};


         addAttributes(identityType, attrs);
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

   public void addAttribute(String id, String attributeName, Object[] values) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(values, "Attribute values");

         IdentityType identityType = createIdentityTypeFromId(id);

         addAttribute(identityType, attributeName, values);
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

   public void addAttribute(String id, String attributeName, Object value) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(value, "Attribute value");

         IdentityType identityType = createIdentityTypeFromId(id);

         addAttribute(identityType, attributeName, value);
      }
      catch (IdentityException e)
      {
         e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }

   }

   public void addAttributes(IdentityType identityType, Attribute[] attributes) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");
         checkNotNullArgument(attributes, "Attributes");


         preAttributesAdd(identityType, attributes);

         getRepository().addAttributes(getInvocationContext(), createIdentityObject(identityType), convertAttributes(attributes));

         if (cache != null)
         {
            // Grab the new profile and persist in cache
            cache.invalidateAttributes(cacheNS, identityType.getKey());
            this.getAttributes(identityType);
         }

         postAttributesAdd(identityType, attributes);
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



   public void removeAttributes(IdentityType identityType, String[] attributeNames) throws IdentityException
   {
      try
      {
         checkNotNullArgument(identityType, "IdentityType");
         checkNotNullArgument(attributeNames, "Attribute names");

         preAttributesRemove(identityType, attributeNames);

         getRepository().removeAttributes(getInvocationContext(), createIdentityObject(identityType), attributeNames);

         if (cache != null)
         {
            // Grab the new profile and persist in cache
            cache.invalidateAttributes(cacheNS, identityType.getKey());
            this.getAttributes(identityType);
         }

         postAttributesRemove(identityType, attributeNames);
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

   public void removeAttributes(String id, String[] attributeNames) throws IdentityException
   {
      try
      {
         checkNotNullArgument(id, "Id (Group) or name (User)");
         checkNotNullArgument(attributeNames, "Attribute names");

         IdentityType identityType = createIdentityTypeFromId(id);

         removeAttributes(identityType, attributeNames);
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

   public boolean hasPassword(User user) throws IdentityException
   {
      checkNotNullArgument(user, "User");
      return getRepository().getSupportedFeatures().isCredentialSupported(createIdentityObject(user).getIdentityType(), PasswordCredential.TYPE);
   }

   public boolean validatePassword(User user, String password) throws IdentityException
   {
      try
      {
         checkNotNullArgument(user, "User");
         checkNotNullArgument(password, "Password");
         return getRepository().validateCredential(getInvocationContext(), createIdentityObject(user), new PasswordCredential(password,
               getCredentialEncoder(), user.getKey()));
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

   public void updatePassword(User user, String password) throws IdentityException
   {
      try
      {
         checkNotNullArgument(user, "User");
         checkNotNullArgument(password, "Password");

         PasswordCredential passwordCredential = new PasswordCredential(password, getCredentialEncoder(), user.getKey());
         preCredentialUpdate(user, passwordCredential);

         getRepository().updateCredential(getInvocationContext(), createIdentityObject(user), passwordCredential);

         postCredentialUpdate(user, passwordCredential);
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

   public boolean isCredentialTypeSupported(CredentialType credentialType) throws IdentityException
   {
      checkNotNullArgument(credentialType, "CredentialType");

      return getRepository().getSupportedFeatures().isCredentialSupported(getUserObjectType(), new SimpleCredentialType(credentialType.getName()));
   }

   public boolean validateCredentials(User user, Credential[] credentials) throws IdentityException
   {
      try
      {
         checkNotNullArgument(user, "User");
         checkNotNullArgument(credentials, "Credentials");

         for (Credential credential : credentials)
         {
            IdentityObjectCredential ioc = null;

            //Handle only those credentials that implement SPI

            if (!(credential instanceof IdentityObjectCredential))
            {
               throw new IdentityException("Unsupported Credential implementation: " + credential.getClass());
            }

            ioc = (IdentityObjectCredential)credential;

            // All credentials must pass

            if (!getRepository().validateCredential(getInvocationContext(), createIdentityObject(user), ioc))
            {
               return false;
            }
         }

         return true;
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

   public void updateCredential(User user, Credential credential) throws IdentityException
   {
      try
      {
         checkNotNullArgument(user, "User");
         checkNotNullArgument(credential, "Credential");

         if (credential instanceof IdentityObjectCredential)
         {
            preCredentialUpdate(user, credential);

            getRepository().updateCredential(getInvocationContext(), createIdentityObject(user), (IdentityObjectCredential)credential);

            postCredentialUpdate(user, credential);
         }
         else
         {
            throw new IdentityException("Unsupported Credential implementation: " + credential.getClass());
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

   public User findUserByUniqueAttribute(String attributeName, Object value) throws IdentityException
   {
      try
      {
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(value, "Attribute value");

         //TODO: cache

         IdentityObject io = getRepository().findIdentityObjectByUniqueAttribute(getInvocationContext(), getUserObjectType(), new SimpleAttribute(attributeName, value));

         if (io == null)
         {
            return null;
         }

         return createUser(io);
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

   public Group findGroupByUniqueAttribute(String groupType, String attributeName, Object value) throws IdentityException
   {
      try
      {
         checkNotNullArgument(groupType, "GroupType");
         checkNotNullArgument(attributeName, "Attribute name");
         checkNotNullArgument(value, "Attribute value");

         //TODO: cache

         IdentityObject io = getRepository().findIdentityObjectByUniqueAttribute(getInvocationContext(),
            getIdentityObjectType(groupType),
            new SimpleAttribute(attributeName, value));

         if (io == null)
         {
            return null;
         }

         return createGroup(io);
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

    /**
     * Change oldPassword of identity to newPassword
     *
     * @param identityName
     * @param oldPassword
     * @param newPassword
     * @return true if successfully changed
     */
    public boolean changePassword(String identityName, String oldPassword, String newPassword) throws IdentityException {
        try
        {
            checkNotNullArgument(identityName, "Identity name");
            checkNotNullArgument(oldPassword, "Old password");
            checkNotNullArgument(newPassword, "New password");

            return getRepository().changePassword(identityName, oldPassword, newPassword);
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

    /**
     * Change identity password to newPassword if challengePairs correct
     *
     * @param identityName
     * @param challengePairs question - key, answer - value
     * @param newPassword
     * @return true if successfully changed
     */
    public boolean forgotPassword(String identityName, Map<String, String> challengePairs, String newPassword) throws IdentityException {
        try
        {
            checkNotNullArgument(identityName, "Identity name");
            checkNotNullArgument(newPassword, "New password");

            return getRepository().forgotPassword(identityName, challengePairs, newPassword);
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

    public List<String> getIdentityChallengeQuestions(String identityName) throws IdentityException {
        try
        {
            return getRepository().getChallengeQuestions(identityName);
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
}
