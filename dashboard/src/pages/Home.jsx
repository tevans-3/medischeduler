import React from 'react';
import { Typography, Box, Paper, Stack, Divider } from '@mui/material';

/**
 * Home page displayed when the user first visits the application.
 *
 * Provides a brief overview of MediScheduler and instructions for
 * getting started. The user uploads CSV files using the buttons in the
 * bottom-right corner of the DashboardLayout.
 */
export default function Home() {
  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', mt: 2 }}>
      <Typography variant="h4" color="#007c41" gutterBottom>
        Welcome to MediScheduler
      </Typography>

      <Typography variant="body1" paragraph>
        Automated scheduling for medical student clerkship rotations. Upload
        your student and teacher CSV files to get started.
      </Typography>

      <Divider sx={{ my: 3 }} />

      <Stack spacing={3}>
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Step 1: Upload Student Data
          </Typography>
          <Typography variant="body2">
            Click the <strong>Upload Students</strong> button in the bottom-right
            corner and select a CSV file with columns: ID, FirstName, LastName,
            EmailAddress, Address, TravelMethods, Session#.
          </Typography>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Step 2: Upload Teacher Data
          </Typography>
          <Typography variant="body2">
            Click the <strong>Upload Teachers</strong> button and select a CSV
            file with columns: ID, FirstName, LastName, EmailAddress, Address,
            Availability.
          </Typography>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Step 3: Validate and Run
          </Typography>
          <Typography variant="body2">
            Once both files are uploaded and validated, the <strong>Run
            Scheduler</strong> button will activate. Click it to begin
            generating optimal assignments. You will be redirected to a
            progress page while the scheduler runs.
          </Typography>
        </Paper>

        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Step 4: Review Results
          </Typography>
          <Typography variant="body2">
            When the scheduler finishes, assignments are displayed on an
            interactive map. You can review, manually edit, and download the
            final assignments.
          </Typography>
        </Paper>
      </Stack>
    </Box>
  );
}
