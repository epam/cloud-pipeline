import React from "react";

export const PathList = ({paths}) => {
  return (
    <ul>
      {
        paths.map((path, index) => (
          <li>
            <a key={index} href="#" style={{display: 'block'}}>
              {path}
            </a>
          </li>
        ))
      }
    </ ul>
  );
};
