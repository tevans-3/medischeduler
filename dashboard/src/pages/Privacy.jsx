import React from 'react';
import { Typography, Box, Paper, Stack, List, ListItem, ListItemText } from '@mui/material';

/**
 * Privacy statement page describing how MediScheduler handles user data.
 */
export default function Privacy() {
  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', mt: 2 }}>
      <Typography variant="h4" color="#007c41" gutterBottom>
        Privacy Statement
      </Typography>

      <Stack spacing={3}>
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Data Handling</Typography>
          <Typography variant="body2" paragraph>
            MediScheduler processes student and teacher data exclusively for the
            purpose of generating clerkship rotation assignments. All data is
            stored in-memory in Redis with namespaced keys per client session.
          </Typography>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Data Retention</Typography>
          <Typography variant="body2" paragraph>
            All uploaded data is ephemeral. On session disconnect, all data
            associated with that client is flushed from Redis. No student or
            teacher data is persisted to disk or retained between sessions.
          </Typography>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Third-Party Services</Typography>
          <List dense>
            <ListItem>
              <ListItemText
                primary="Google Routes API"
                secondary="Student and teacher addresses are sent to Google's Routes API to compute commute distances. Google's privacy policy governs this data."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Google Maps"
                secondary="The results page uses an embedded Google Map to display assignments. No personal data is sent to Google Maps beyond the map tile requests."
              />
            </ListItem>
          </List>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Open Source</Typography>
          <Typography variant="body2" paragraph>
            MediScheduler is completely free and open-source. The full source
            code is available for audit. No telemetry, analytics, or tracking
            is included in the application.
          </Typography>
        </Paper>
      </Stack>
    </Box>
  );
}
