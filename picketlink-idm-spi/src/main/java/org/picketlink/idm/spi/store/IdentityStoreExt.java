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
    boolean changePassword(String identityName, String oldPassword, String newPassword) throws IdentityException;

    /**
     *
     * @param identityName
     * @param challengePairs
     * @param newPassword
     * @return
     */
    boolean forgotPassword(String identityName, Map<String, String> challengePairs, String newPassword) throws IdentityException;

    /**
     *
     * @param invocationContext
     * @param identityName
     * @param attributes
     * @return
     * @throws IdentityException
     */

    IdentityObject createIdentityObject(IdentityStoreInvocationContext invocationContext, String identityName, Map<String, String> attributes) throws IdentityException;

    /**
     *
     * @param identityName
     * @return
     * @throws IdentityException
     */
    List<String> getChallengeQuestions(String identityName)throws IdentityException;
}
