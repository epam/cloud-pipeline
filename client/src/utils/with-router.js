import React from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';

export function withRouter(Component) {
  function Wrapper(props) {
    const navigate = useNavigate();
    const params = useParams();
    const location = useLocation();
    // Mirror v3's `router` prop shape for backward compatibility
    const router = { push: navigate, replace: (to) => navigate(to, { replace: true }), location };
    return <Component {...props} router={router} params={params} location={location} />;
  }
  Wrapper.displayName = `WithRouter(${Component.displayName || Component.name})`;
  return Wrapper;
}
