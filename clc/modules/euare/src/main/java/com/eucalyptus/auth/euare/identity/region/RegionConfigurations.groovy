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
package com.eucalyptus.auth.euare.identity.region

import com.eucalyptus.configurable.ConfigurableClass
import com.eucalyptus.configurable.ConfigurableField
import com.eucalyptus.configurable.ConfigurableProperty
import com.eucalyptus.configurable.ConfigurablePropertyException
import com.eucalyptus.configurable.PropertyChangeListener
import com.eucalyptus.util.Exceptions
import com.eucalyptus.util.UpperCamelPropertyNamingStrategy
import com.google.common.base.Optional
import com.google.common.base.Strings
import com.google.common.net.InternetDomainName
import groovy.transform.CompileStatic
import org.codehaus.jackson.JsonProcessingException
import org.codehaus.jackson.map.ObjectMapper
import org.springframework.context.MessageSource
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.ValidationUtils

import javax.annotation.Nonnull

/**
 *
 */
@ConfigurableClass(
    root = "region",
    description = "Configuration for cloud regions."
)
@CompileStatic
class RegionConfigurations {

  private static final boolean validateConfiguration =
      Boolean.valueOf( System.getProperty( 'com.eucalyptus.auth.euare.identity.region.validateRegionConfiguration', 'true' ) )

  @ConfigurableField(
      description = "Region configuration document.",
      changeListener = RegionConfigurationPropertyChangeListener )
  public static String REGION_CONFIGURATION = "";

  @ConfigurableField(
      description = "Region name.",
      changeListener = RegionNamePropertyChangeListener )
  public static String REGION_NAME = "";

  static RegionConfiguration parse( final String configuration ) throws Exception {
    final ObjectMapper mapper = new ObjectMapper( )
    mapper.setPropertyNamingStrategy( new UpperCamelPropertyNamingStrategy( ) )
    final RegionConfiguration regionConfiguration
    try {
      regionConfiguration = mapper.readValue( new StringReader( configuration ){
        @Override String toString() { "property" } // overridden for better source in error message
      }, RegionConfiguration.class )
    } catch ( JsonProcessingException e ) {
      throw new Exception( e.getMessage( ) )
    }
    final BeanPropertyBindingResult errors = new BeanPropertyBindingResult( regionConfiguration, "RegionConfiguration");
    ValidationUtils.invokeValidator( new RegionConfigurationValidator(errors), regionConfiguration, errors )
    if ( validateConfiguration && errors.hasErrors( ) ) {
      MessageSource source = new StaticMessageSource( ) // default messages will be used
      throw new Exception( source.getMessage( errors.getAllErrors( ).get( 0 ), Locale.getDefault( ) ) )
    }
    regionConfiguration
  }

  /**
   * Get the region configuration.
   *
   * @return The configuration (if set)
   */
  @Nonnull
  static Optional<RegionConfiguration> getRegionConfiguration( ) {
    Optional<RegionConfiguration> configuration = Optional.absent( )
    String configurationText = REGION_CONFIGURATION
    if ( !Strings.isNullOrEmpty( configurationText ) ) {
      try {
        configuration = Optional.of( parse( configurationText ) )
      } catch ( Exception e ) {
        throw Exceptions.toUndeclared( e )
      }
    }
    configuration
  }

  /**
   * Get the name of the local region
   *
   * @return The name (if set)
   */
  @Nonnull
  static Optional<String> getRegionName( ) {
    return Optional.fromNullable( Strings.emptyToNull( REGION_NAME ) )
  }

  static class RegionNamePropertyChangeListener implements PropertyChangeListener<String> {
    @Override
    void fireChange( final ConfigurableProperty property,
                     final String newValue ) throws ConfigurablePropertyException {
      if ( !Strings.isNullOrEmpty( newValue ) ) try {
        final InternetDomainName name = InternetDomainName.from( String.format( "${newValue}.com" ) )
        if ( name.parts( ).size( ) != 2 ) {
          throw new ConfigurablePropertyException( "Invalid region name: ${newValue}" );
        }
      } catch ( Exception e ) {
        throw new ConfigurablePropertyException( "Invalid region name: ${newValue}", e )
      }
    }
  }

  static class RegionConfigurationPropertyChangeListener implements PropertyChangeListener<String> {
    @Override
    void fireChange( final ConfigurableProperty property,
                     final String newValue ) throws ConfigurablePropertyException {
      if ( !Strings.isNullOrEmpty( newValue ) ) {
        try {
          parse( newValue )
        } catch ( e ) {
          throw new ConfigurablePropertyException( e.getMessage( ), e )
        }
      }
    }
  }
}
