package fr.labri.harmony.extractor.github;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.egit.github.core.Commit;
import org.eclipse.egit.github.core.CommitFile;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.RepositoryCommitCompare;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.RepositoryService;

import fr.labri.harmony.core.config.model.SourceConfiguration;
import fr.labri.harmony.core.dao.Dao;
import fr.labri.harmony.core.log.HarmonyLogger;
import fr.labri.harmony.core.model.Action;
import fr.labri.harmony.core.model.ActionKind;
import fr.labri.harmony.core.model.Author;
import fr.labri.harmony.core.model.Event;
import fr.labri.harmony.core.model.Item;
import fr.labri.harmony.core.source.AbstractSourceExtractor;
import fr.labri.harmony.core.source.DefaultWorkspace;
import fr.labri.harmony.core.source.SourceExtractorException;
import fr.labri.harmony.core.source.WorkspaceException;

public class GitHubExtractor extends AbstractSourceExtractor<DefaultWorkspace> {

	public final static String GITHUB_OAUTH_TOKEN = "oauth-token";

	public GitHubExtractor() {
		super();
	}

	public GitHubExtractor(SourceConfiguration config, Dao dao, Properties properties) {
		super(config, dao, properties);
	}

	private GitHubClient ghClient;
	private Repository repository;

	@Override
	public void initializeWorkspace() {
		ghClient = new GitHubClient();
		ghClient.setOAuth2Token(config.getOptions().get(GITHUB_OAUTH_TOKEN));

		String[] splittedUrl = getUrl().split("/");
		String owner = splittedUrl[splittedUrl.length - 2];
		String repoName = splittedUrl[splittedUrl.length - 1];
		RepositoryService repoService = new RepositoryService(ghClient);
		try {
			repository = repoService.getRepository(owner, repoName);
		} catch (IOException e) {
			throw new WorkspaceException(e);
		}

		workspace = new DefaultWorkspace(this);

	}

	@Override
	public void extractEvents() {
		
		// TODO : we have to regularly check the remaining request quota, and wait for the quota to increase if necessary.

		CommitService commitService = new CommitService(ghClient);
		try {
			List<RepositoryCommit> commits = commitService.getCommits(repository);
			// We iterate backwards on the list to have the parent commits first
			for (int i = commits.size() - 1; i >= 0; i--) {
				RepositoryCommit repoCommit = commits.get(i);
				Commit commit = repoCommit.getCommit();
				String commiterName = commit.getCommitter().getName();

				Author a = getAuthor(commiterName);
				if (a == null) {
					a = new Author(getSource(), commiterName, commiterName);
					saveAuthor(a);
				}
				ArrayList<Author> authors = new ArrayList<>();
				authors.add(a);

				List<Event> parents = new ArrayList<>();
				List<Commit> parentCommits = repoCommit.getParents();
				if (parentCommits != null) {
					for (Commit parent : parentCommits) {
						Event e = getEvent(parent.getSha());
						if (e == null) {
							HarmonyLogger.error("one of the parent events was not loaded");
						} else parents.add(e);
					}
				}

				Event event = new Event(getSource(), repoCommit.getSha(), commit.getCommitter().getDate().getTime(), parents, authors);
				saveEvent(event);
			}
			
			HarmonyLogger.info("Extraction of Events finished");
			HarmonyLogger.info("GitHub API quota : " + ghClient.getRemainingRequests());
			
		} catch (IOException e) {
			throw new SourceExtractorException(e);
		}

	}

	@Override
	public void extractActions(Event event) {
		CommitService commitService = new CommitService(ghClient);
		try {
			for (Event parentEvent : event.getParents()) {
				RepositoryCommitCompare compare = commitService.compare(repository, parentEvent.getNativeId(), event.getNativeId());
				for (CommitFile commitFile : compare.getFiles()) {
					Action a = new Action();
					a.setEvent(event);
					a.setParentEvent(parentEvent);
					a.setSource(getSource());

					// set action kind
					switch (commitFile.getStatus()) {
					case "added":
						a.setKind(ActionKind.Create);
						break;
					case "modified":
						a.setKind(ActionKind.Edit);
						break;
					case "deleted":
						a.setKind(ActionKind.Delete);
						break;
					default:
						break;
					}

					Item i = getItem(commitFile.getFilename());
					if (i == null) {
						i = new Item(getSource(), commitFile.getFilename());
						saveItem(i);
					}

					a.setItem(i);
					saveAction(a);
				}
			}


		} catch (IOException e) {
			throw new SourceExtractorException(e);
		}
		
		HarmonyLogger.info("Extraction of Actions finished");
		HarmonyLogger.info("GitHub API quota : " + ghClient.getRemainingRequests());

	}
}
