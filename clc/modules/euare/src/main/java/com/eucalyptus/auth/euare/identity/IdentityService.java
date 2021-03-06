/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.auth.euare.identity;

import static com.eucalyptus.auth.principal.Certificate.Util.revoked;
import static com.eucalyptus.util.CollectionUtils.propertyPredicate;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.InvalidAccessKeyAuthException;
import com.eucalyptus.auth.api.IdentityProvider;
import com.eucalyptus.auth.euare.EuareException;
import com.eucalyptus.auth.euare.common.identity.DescribePrincipalResponseType;
import com.eucalyptus.auth.euare.common.identity.DescribePrincipalResult;
import com.eucalyptus.auth.euare.common.identity.DescribePrincipalType;
import com.eucalyptus.auth.euare.common.identity.Policy;
import com.eucalyptus.auth.euare.common.identity.Principal;
import com.eucalyptus.auth.principal.AccessKey;
import com.eucalyptus.auth.principal.Certificate;
import com.eucalyptus.auth.principal.PolicyVersion;
import com.eucalyptus.auth.principal.PolicyVersions;
import com.eucalyptus.auth.principal.UserPrincipal;
import com.eucalyptus.component.annotation.ComponentNamed;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.util.Exceptions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 *
 */
@ComponentNamed
public class IdentityService {

  private static final Logger logger = Logger.getLogger( IdentityService.class );

  private IdentityProvider identityProvider;

  @Inject
  public IdentityService( @Named( "localIdentityProvider" ) final IdentityProvider identityProvider ) {
    this.identityProvider = identityProvider;
  }

  public DescribePrincipalResponseType describePrincipal( final DescribePrincipalType request ) throws EuareException {
    final DescribePrincipalResponseType response = request.getReply( );
    final DescribePrincipalResult result = new DescribePrincipalResult( );

    try {
      final UserPrincipal user;
      if ( request.getAccessKeyId( ) != null ) {
        user = identityProvider.lookupPrincipalByAccessKeyId( request.getAccessKeyId( ), request.getNonce( ) );
      } else if ( request.getCertificateId( ) != null ) {
        user = identityProvider.lookupPrincipalByCertificateId( request.getCertificateId( ) );
      } else if ( request.getUserId( ) != null ) {
        user = identityProvider.lookupPrincipalByUserId( request.getUserId( ), request.getNonce( ) );
      } else if ( request.getRoleId( ) != null ) {
        user = identityProvider.lookupPrincipalByRoleId( request.getRoleId( ), request.getNonce( ) );
      } else if ( request.getAccountId( ) != null ) {
        user = identityProvider.lookupPrincipalByAccountNumber( request.getAccountId( ) );
      } else if ( request.getCanonicalId( ) != null ) {
        user = identityProvider.lookupPrincipalByCanonicalId( request.getCanonicalId( ) );
      } else {
        user = null;
      }

      if ( user != null ) {
        final Principal principal = new Principal( );
        principal.setEnabled( user.isEnabled( ) );
        principal.setArn( Accounts.getUserArn( user ) );
        principal.setUserId( user.getUserId( ) );
        principal.setRoleId( Accounts.isRoleIdentifier( user.getAuthenticatedId( ) ) ?
                user.getAuthenticatedId( ) :
                null
        );
        principal.setCanonicalId( user.getCanonicalId( ) );
        principal.setAccountAlias( user.getAccountAlias() );

        final ArrayList<com.eucalyptus.auth.euare.common.identity.AccessKey> accessKeys = Lists.newArrayList( );
        for ( final AccessKey accessKey : user.getKeys( ) ) {
          final com.eucalyptus.auth.euare.common.identity.AccessKey key =
              new com.eucalyptus.auth.euare.common.identity.AccessKey( );
          key.setAccessKeyId( accessKey.getAccessKey( ) );
          key.setSecretAccessKey( accessKey.getSecretKey( ) );
          accessKeys.add( key );
        }
        principal.setAccessKeys( accessKeys );

        final ArrayList<com.eucalyptus.auth.euare.common.identity.Certificate> certificates = Lists.newArrayList( );
        for ( final Certificate certificate :
            Iterables.filter( user.getCertificates(), propertyPredicate( false, revoked() ) ) ) {
          final com.eucalyptus.auth.euare.common.identity.Certificate cert =
              new com.eucalyptus.auth.euare.common.identity.Certificate();
          cert.setCertificateId( certificate.getCertificateId() );
          cert.setCertificateBody( certificate.getPem() );
          certificates.add( cert );
        }
        principal.setCertificates( certificates );

        final ArrayList<Policy> policies = Lists.newArrayList( );
        if ( user.isEnabled( ) ) for ( final PolicyVersion policyVersion : user.getPrincipalPolicies() ) {
          final Policy policy = new Policy();
          policy.setVersionId( policyVersion.getPolicyVersionId( ) );
          policy.setName( policyVersion.getPolicyName( ) );
          policy.setScope( policyVersion.getPolicyScope( ).toString() );
          policy.setPolicy( policyVersion.getPolicy( ) );
          policy.setHash( PolicyVersions.hash( policyVersion.getPolicy( ) ) );
          policies.add( policy );
        }
        principal.setPolicies( policies );

        result.setPrincipal( principal );
      }
    } catch ( InvalidAccessKeyAuthException e ) {
      // not found, so empty response
    } catch ( AuthException e ) {
      throw handleException( e );
    }

    response.setDescribePrincipalResult( result );
    return response;
  }

  /**
   * Method always throws, signature allows use of "throw handleException ..."
   */
  private EuareException handleException( final Exception e ) throws EuareException {
    final EuareException cause = Exceptions.findCause( e, EuareException.class );
    if ( cause != null ) {
      throw cause;
    }

    logger.error( e, e );

    final EuareException exception =
        new EuareException( HttpResponseStatus.INTERNAL_SERVER_ERROR, "InternalError", String.valueOf(e.getMessage( )) );
    if ( Contexts.lookup( ).hasAdministrativePrivileges( ) ) {
      exception.initCause( e );
    }
    throw exception;
  }

}
