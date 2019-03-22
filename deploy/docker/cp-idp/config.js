
/**
 * Default user Profile
 */
var profile = {
    userName: 'noone@nowhere.com',
    nameIdFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress',
    firstName: 'noone',
    lastName: 'noone',
    displayName: 'noone noone',
    email: 'noone@nowhere.com',
    groups: 'noone'
  }
  
  /**
   * SAML Attribute Metadata
   */
  var metadata = [{
    id: "firstName",
    optional: false,
    displayName: 'First Name',
    description: 'The given name of the user',
    multiValue: false
  }, {
    id: "lastName",
    optional: false,
    displayName: 'Last Name',
    description: 'The surname of the user',
    multiValue: false
  }, {
    id: "email",
    optional: false,
    displayName: 'E-Mail Address',
    description: 'The e-mail address of the user',
    multiValue: false
  }, {
    id: "groups",
    optional: true,
    displayName: 'Groups',
    description: 'Group memberships of the user',
    multiValue: true
  } ];
  
  /**
   * HTML settings
   */
  var appearance = {
    title: 'Cloud Pipeline Sign In',
    icon: '/logo.png'
  };
  
  module.exports = {
    appearance: appearance,
    user: profile,
    metadata: metadata
  }