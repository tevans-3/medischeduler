import React from 'react';
import { Typography, Box, Paper, Stack, Divider, List, ListItem, ListItemText } from '@mui/material';

/**
 * User documentation page explaining how to use MediScheduler.
 *
 * Covers CSV formatting requirements, the scheduling workflow, and
 * how to interpret results.
 */
export default function UserDocs() {
  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', mt: 2 }}>
      <Typography variant="h4" color="#007c41" gutterBottom>
        User Documentation
      </Typography>

      <Stack spacing={3}>
        {/* Overview */}
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Overview</Typography>
          <Typography variant="body2" paragraph>
            MediScheduler automates the scheduling of medical student clerkship
            rotations. Administrators upload student and teacher data as CSV files,
            the system computes commute distances for every possible pairing, and
            an optimization solver generates the best assignment of students to
            preceptors.
          </Typography>
        </Paper>

        {/* CSV Format */}
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>CSV Formatting</Typography>
          <Typography variant="body2" paragraph>
            Both CSV files must include a header row. Column order matters.
          </Typography>

          <Typography variant="subtitle2" sx={{ mt: 1 }}>Student CSV columns:</Typography>
          <List dense>
            <ListItem><ListItemText primary="ID" secondary="Unique student identifier" /></ListItem>
            <ListItem><ListItemText primary="FirstName" secondary="Student's first name" /></ListItem>
            <ListItem><ListItemText primary="LastName" secondary="Student's last name" /></ListItem>
            <ListItem><ListItemText primary="EmailAddress" secondary="Contact email" /></ListItem>
            <ListItem><ListItemText primary="Address" secondary="Full home address for commute calculations" /></ListItem>
            <ListItem><ListItemText primary="TravelMethods" secondary="Preferred travel mode: DRIVE, TRANSIT, BIKE, or WALK" /></ListItem>
            <ListItem><ListItemText primary="Session#" secondary="Track/session number the student belongs to" /></ListItem>
          </List>

          <Divider sx={{ my: 2 }} />

          <Typography variant="subtitle2">Teacher CSV columns:</Typography>
          <List dense>
            <ListItem><ListItemText primary="ID" secondary="Unique teacher identifier" /></ListItem>
            <ListItem><ListItemText primary="FirstName" secondary="Teacher's first name" /></ListItem>
            <ListItem><ListItemText primary="LastName" secondary="Teacher's last name" /></ListItem>
            <ListItem><ListItemText primary="EmailAddress" secondary="Contact email" /></ListItem>
            <ListItem><ListItemText primary="Address" secondary="Clinic/office address" /></ListItem>
            <ListItem><ListItemText primary="Availability" secondary="Which sessions/tracks the teacher can accept students for" /></ListItem>
          </List>
        </Paper>

        {/* Workflow */}
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Workflow</Typography>
          <List dense>
            <ListItem>
              <ListItemText
                primary="1. Upload CSVs"
                secondary="Use the Upload Students and Upload Teachers buttons on the home page."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="2. Validation"
                secondary="The system validates that all required columns are present and data is correctly formatted."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="3. Run Scheduler"
                secondary="Once both files pass validation, click Run Scheduler to begin."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="4. Route Computation"
                secondary="The system computes commute distances using the Google Routes API. Progress is shown in real time."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="5. Optimization"
                secondary="The OR-Tools solver generates the best assignment respecting all constraints."
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="6. Review Results"
                secondary="View assignments on the map, make manual edits if needed, and download the final CSV."
              />
            </ListItem>
          </List>
        </Paper>

        {/* Constraints */}
        <Paper elevation={2} sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Scheduling Constraints</Typography>
          <List dense>
            <ListItem>
              <ListItemText primary="Each preceptor is assigned at most one student per track." />
            </ListItem>
            <ListItem>
              <ListItemText primary="Non-driving students are assigned the shortest possible commute." />
            </ListItem>
            <ListItem>
              <ListItemText primary="Student specialty preferences are matched to teacher specialties where possible." />
            </ListItem>
            <ListItem>
              <ListItemText primary="Teachers are only assigned students from tracks they are available for." />
            </ListItem>
          </List>
        </Paper>
      </Stack>
    </Box>
  );
}
