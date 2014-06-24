package org.picketlink.idm.spi.store;

import org.picketlink.idm.common.exception.IdentityException;
import org.picketlink.idm.spi.model.IdentityObject;

import java.util.List;
import java.util.Map;

/**
 * Created by anton.riabov on 3/27/14.
 */
public interface IdentityStoreExt extends IdentityStore {
    /**
     *
     * @param identityName
     * @param oldPassword
     * @param newPassword
     * @return
     */
    void changePassword(String identityName, String oldPassword, String newPassword) throws IdentityException;

    /**
     *
     * @param identityName
     * @param challengePairs
     * @param newPassword
     * @return
     */
    void forgotPassword(String identityName, Map<String, String> challengePairs, String newPassword) throws IdentityException;

    /**
     *
     * @param invocationContext
     * @param identityName
     * @param attributes
     * @return
     * @throws IdentityException
     */

    IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationContext, String identityName, Map<String, List<String>> attributes) throws IdentityException;

    /**
     *
     * @param identityName
     * @return
     * @throws IdentityException
     */
    List<String> getChallengeQuestions(String identityName)throws IdentityException;

    /**
     *
     * @param identityName
     * @param attributes
     * @throws IdentityException
     */
    void updateIdentityObjectAttributes(String identityName, Map<String, List<String>> attributes) throws IdentityException;

	/**
	 * Gets a role list by person id.
	 *
	 * @param userId
	 * @return role list
	 * @throws IdentityException
	 */
	List<String> getPersonRoles(String userId) throws IdentityException;

    /**
     *
     * @param identityName
     * @throws IdentityException
     */
    void deleteIdentityObject(String identityName) throws IdentityException;
}