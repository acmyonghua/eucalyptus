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
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.walrus.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;
import org.apache.xml.security.utils.Base64;

import com.eucalyptus.auth.AccessKeys;
import com.eucalyptus.auth.Accounts;
import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.api.BaseLoginModule;
import com.eucalyptus.auth.login.AuthenticationException;
import com.eucalyptus.auth.principal.AccessKey;
import com.eucalyptus.auth.principal.Account;
import com.eucalyptus.auth.principal.UserPrincipal;
import com.eucalyptus.crypto.Hmac;
import com.eucalyptus.walrus.exceptions.AccessDeniedException;

public class WalrusLoginModule extends BaseLoginModule<WalrusWrappedCredentials> {
  private static Logger LOG = Logger.getLogger(WalrusLoginModule.class);

  public WalrusLoginModule() {}

  @Override
  public boolean accepts() {
    return super.getCallbackHandler() instanceof WalrusWrappedCredentials;
  }

  @Override
  public boolean authenticate(WalrusWrappedCredentials credentials) throws Exception {
    String signature = credentials.getSignature().replaceAll("=", "");
    final AccessKey key = AccessKeys.lookupAccessKey(credentials.getQueryId(), credentials.getSecurityToken());
    if (!key.isActive()) {
      throw new AuthenticationException("The AWS Access Key Id you provided does not exist in our records.");
    }
    final UserPrincipal user = key.getPrincipal();
    final String queryKey = key.getSecretKey();
    final String authSig = checkSignature(queryKey, credentials.getLoginData());
    if (authSig.equals(signature)) {
      try {
        if (!Account.OBJECT_STORAGE_WALRUS_ACCOUNT.equals( Accounts.lookupAccountAliasById( user.getAccountNumber( ) ) )) {
          throw new AccessDeniedException("walrus only accepts requests from " + Account.OBJECT_STORAGE_WALRUS_ACCOUNT);
        }
      } catch (AuthException e) {
        throw new AccessDeniedException(e.getMessage());
      }
      super.setCredential(credentials.getQueryIdCredential());
      super.setPrincipal(user);
      return true;
    }
    return false;
  }

  @Override
  public void reset() {}

  protected String checkSignature(final String queryKey, final String subject) throws AuthenticationException {
    SecretKeySpec signingKey = new SecretKeySpec(queryKey.getBytes(), Hmac.HmacSHA1.toString());
    try {
      Mac mac = Mac.getInstance(Hmac.HmacSHA1.toString());
      mac.init(signingKey);
      byte[] rawHmac = mac.doFinal(subject.getBytes());
      return new String(Base64.encode(rawHmac)).replaceAll("=", "");
    } catch (Exception e) {
      LOG.error(e, e);
      throw new AuthenticationException("Failed to compute signature");
    }
  }
}
