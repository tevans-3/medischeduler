import React from 'react';
import { Button, CircularProgress } from '@mui/material';

/**
 * A button that shows a loading spinner when processing.
 *
 * Props:
 *   loading  - boolean, shows spinner when true
 *   children - button label text
 *   ...rest  - any other props passed to MUI Button
 */
export default function LoadingButton({ loading = false, children, ...rest }) {
  return (
    <Button
      disabled={loading}
      {...rest}
    >
      {loading ? <CircularProgress size={24} color="inherit" /> : children}
    </Button>
  );
}
