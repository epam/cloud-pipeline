import React from 'react';

export default function UserAttributes (
  {
    className,
    attributeClassName,
    user,
    skip = [],
    style
  }
) {
  if (Object.values(user.attributes || {}).length > 0) {
    const extractAttributes = (...names) => {
      let keys = Object
        .keys(user.attributes || {})
        .sort((a, b) => {
          let indexA = names.map(name => name.toLowerCase()).indexOf(a.toLowerCase());
          let indexB = names.map(name => name.toLowerCase()).indexOf(b.toLowerCase());
          if (indexA === -1) {
            indexA = Infinity;
          }
          if (indexB === -1) {
            indexB = Infinity;
          }
          return indexA - indexB;
        });
      const skipSet = new Set(skip.map(o => o.toLowerCase()));
      if (keys.filter(key => !skipSet.has(key.toLowerCase())).length > 0) {
        keys = keys.filter(key => !skipSet.has(key.toLowerCase()));
      }
      const values = [
        ...new Set(
          keys
            .filter(key => user.attributes.hasOwnProperty(key) && user.attributes[key])
            .map(key => user.attributes[key])
        )];
      return values
        .map(value => (
          <span key={value} className={attributeClassName}>
            {value}
          </span>
        ));
    };
    return (
      <div className={className} style={style}>
        {
          extractAttributes(
            'name',
            'first name',
            'firstname',
            'last name',
            'lastname'
          )
        }
      </div>
    );
  }
  if (user.name || user.userName) {
    return (
      <div className={className} style={style}>
        <span className={attributeClassName}>{user.name || user.userName}</span>
      </div>
    );
  }
  return null;
}
