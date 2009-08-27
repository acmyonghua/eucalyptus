/*******************************************************************************
*Copyright (c) 2009  Eucalyptus Systems, Inc.
* 
*  This program is free software: you can redistribute it and/or modify
*  it under the terms of the GNU General Public License as published by
*  the Free Software Foundation, only version 3 of the License.
* 
* 
*  This file is distributed in the hope that it will be useful, but WITHOUT
*  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
*  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
*  for more details.
* 
*  You should have received a copy of the GNU General Public License along
*  with this program.  If not, see <http://www.gnu.org/licenses/>.
* 
*  Please contact Eucalyptus Systems, Inc., 130 Castilian
*  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
*  if you need additional information or have any questions.
* 
*  This file may incorporate work covered under the following copyright and
*  permission notice:
* 
*    Software License Agreement (BSD License)
* 
*    Copyright (c) 2008, Regents of the University of California
*    All rights reserved.
* 
*    Redistribution and use of this software in source and binary forms, with
*    or without modification, are permitted provided that the following
*    conditions are met:
* 
*      Redistributions of source code must retain the above copyright notice,
*      this list of conditions and the following disclaimer.
* 
*      Redistributions in binary form must reproduce the above copyright
*      notice, this list of conditions and the following disclaimer in the
*      documentation and/or other materials provided with the distribution.
* 
*    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
*    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
*    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
*    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
*    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
*    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
*    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
*    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
*    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
*    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
*    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
*    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
*    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
*    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
*    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
*    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
*    THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
*    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
*    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
*    ANY SUCH LICENSES OR RIGHTS.
*******************************************************************************/
/**
 * 
 */
/*
 * Author: chris grzegorczyk <grze@eucalyptus.com>
 */
package com.eucalyptus.auth;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.zip.Adler32;

import org.bouncycastle.util.encoders.UrlBase64;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.MatchMode;

import com.eucalyptus.auth.Hashes.Digest;
import com.eucalyptus.bootstrap.Bootstrapper;
import com.eucalyptus.bootstrap.Component;
import com.eucalyptus.bootstrap.Depends;
import com.eucalyptus.bootstrap.Provides;
import com.eucalyptus.bootstrap.Resource;
import com.eucalyptus.util.EntityWrapper;
import com.eucalyptus.util.EucalyptusCloudException;
import com.google.common.collect.Lists;

@Provides( resource = Resource.UserCredentials )
@Depends( resources = { Resource.Database } )
public class UserCredentialProvider extends Bootstrapper {
  public static boolean hasCertificate( final String alias ) {
    X509Cert certInfo = null;
    EntityWrapper<X509Cert> db = Credentials.getEntityWrapper( );
    try {
      certInfo = db.getUnique( new X509Cert( alias ) );
    } catch ( EucalyptusCloudException e ) {
    } finally {
      db.commit( );
    }
    return certInfo != null;
  }

  public static X509Certificate getCertificate( final String alias ) throws GeneralSecurityException {
    EntityWrapper<X509Cert> db = Credentials.getEntityWrapper( );
    try {
      X509Cert certInfo = db.getUnique( new X509Cert( alias ) );
      byte[] certBytes = UrlBase64.decode( certInfo.getPemCertificate( ).getBytes( ) );
      X509Certificate x509 = Hashes.getPemCert( certBytes );
      return x509;
    } catch ( EucalyptusCloudException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
  }

  public static String getCertificateAlias( final String certPem ) throws GeneralSecurityException {
    String certAlias = null;
    EntityWrapper<X509Cert> db = Credentials.getEntityWrapper( );
    X509Cert certInfo = new X509Cert( );
    certInfo.setPemCertificate( new String( UrlBase64.encode( certPem.getBytes( ) ) ) );
    try {
      certAlias = db.getUnique( certInfo ).getAlias( );
    } catch ( EucalyptusCloudException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
    return certAlias;
  }

  public static String getQueryId( String userName ) throws GeneralSecurityException {
    String queryId = null;
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    User searchUser = new User( userName );
    try {
      User user = db.getUnique( searchUser );
      queryId = user.getQueryId( );
    } catch ( EucalyptusCloudException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
    return queryId;
  }

  public static String getSecretKey( String queryId ) throws GeneralSecurityException {
    String secretKey = null;
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    User searchUser = new User( );
    searchUser.setQueryId( queryId );
    try {
      User user = db.getUnique( searchUser );
      secretKey = user.getSecretKey( );
    } catch ( EucalyptusCloudException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
    return secretKey;
  }

  public static String getUserName( String queryId ) throws GeneralSecurityException {
    String userName = null;
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    User searchUser = new User( );
    searchUser.setQueryId( queryId );
    try {
      User user = db.getUnique( searchUser );
      userName = user.getUserName( );
    } catch ( EucalyptusCloudException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
    return userName;
  }

  @SuppressWarnings( "unchecked" )
  public static String getUserName( X509Certificate cert ) throws GeneralSecurityException {
    String certPem = new String( UrlBase64.encode( Hashes.getPemBytes( cert ) ) );
    User searchUser = new User( );
    X509Cert searchCert = new X509Cert( );
    searchCert.setPemCertificate( certPem );

    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    try {
      Session session = db.getSession( );
      Example qbeUser = Example.create( searchUser ).enableLike( MatchMode.EXACT );
      Example qbeCert = Example.create( searchCert ).enableLike( MatchMode.EXACT );
      List<User> users = ( List<User> ) session.createCriteria( User.class ).add( qbeUser ).createCriteria( "certificates" ).add( qbeCert ).list( );
      if ( users.size( ) > 1 ) {
        throw new GeneralSecurityException( "Multiple users with the same certificate." );
      } else if ( users.size( ) < 1 ) { throw new GeneralSecurityException( "No user with the specified certificate." ); }
      return users.get( 0 ).getUserName( );
    } catch ( HibernateException e ) {
      throw new GeneralSecurityException( e );
    } finally {
      db.commit( );
    }
  }

  public static String getCertificateAlias( final X509Certificate cert ) throws GeneralSecurityException {
    return getCertificateAlias( new String( Hashes.getPemBytes( cert ) ) );
  }

  public static void addCertificate( final String userName, final String alias, final X509Certificate cert ) throws GeneralSecurityException {
    String certPem = new String( UrlBase64.encode( Hashes.getPemBytes( cert ) ) );
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    User u = null;
    try {
      u = db.getUnique( new User( userName ) );
      X509Cert x509cert = new X509Cert( alias );
      x509cert.setPemCertificate( certPem );
      u.getCertificates( ).add( x509cert );
      db.commit( );
    } catch ( EucalyptusCloudException e ) {
      Credentials.LOG.error( e, e );
      Credentials.LOG.error( "username=" + userName + " \nalias=" + alias + " \ncert=" + cert );
      db.rollback( );
      throw new GeneralSecurityException( e );
    }
  }

  public static List<String> getAliases( ) {
    EntityWrapper<X509Cert> db = Credentials.getEntityWrapper( );
    List<String> certAliases = Lists.newArrayList( );
    try {
      List<X509Cert> certList = db.query( new X509Cert( ) );
      for ( X509Cert cert : certList ) {
        certAliases.add( cert.getAlias( ) );
      }
    } finally {
      db.commit( );
    }
    return certAliases;
  }

  @Override
  public boolean load( Resource current ) throws Exception {
    return true;//TODO: check the DB connection here.
  }

  @Override
  public boolean start( ) throws Exception {
    return Credentials.checkAdmin( );
  }

  public static String getUserNumber( final String userName ) {
    Adler32 hash = new Adler32();
    hash.reset();
    hash.update( userName.getBytes(  ) );
    String userNumber = String.format( "%012d", hash.getValue() );
    return userNumber;
  }

  public static User getUser( String userName ) throws NoSuchUserException {
    User user = null;
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    User searchUser = new User( userName );
    try {
      user = db.getUnique( searchUser );
    } catch ( EucalyptusCloudException e ) {
      throw new NoSuchUserException( e );
    } finally {
      db.commit( );
    }
    return user;
  }

  public static User addUser( String userName, Boolean isAdmin ) throws UserExistsException {
    User newUser = new User( );
    newUser.setUserName( userName );
    String queryId = Hashes.getDigestBase64( userName, Hashes.Digest.SHA224, false ).replaceAll( "\\p{Punct}", "" );
    String secretKey = Hashes.getDigestBase64( userName, Hashes.Digest.SHA224, true ).replaceAll( "\\p{Punct}", "" );
    newUser.setQueryId( queryId );
    newUser.setSecretKey( secretKey );
    newUser.setIsAdministrator( isAdmin );
    EntityWrapper<User> db = Credentials.getEntityWrapper( );
    try {
      db.add( newUser );
    } catch ( Exception e ) {
      db.rollback( );
      throw new UserExistsException( e );
    } finally {
      db.commit( );
    }
    return newUser;
  }

  public static User addUser( String userName ) throws UserExistsException {
    return UserCredentialProvider.addUser( userName, false );
  }
}
